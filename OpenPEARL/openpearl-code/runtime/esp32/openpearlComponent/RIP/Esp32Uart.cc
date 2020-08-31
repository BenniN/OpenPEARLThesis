/**
  * ESP32 UART Comms Driver Wrapper
  * Author: Patrick Scherer
  *
  * A very basic OpenPEARL device wrapper used to communicate via uartComms.
  */

  /*
   [The "BSD license"]
   Copyright (c) 2018-2019 Patrick Scherer
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   1. Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
   2. Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
   3. The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR
   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
   OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
   IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
   NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  */


#include "FreeRTOSConfig.h"
#include "FreeRTOS.h"
#include "task.h"
#include "semphr.h"
//#include "allTaskPriorities.h"

#include "Esp32Uart.h"
#include "Log.h"

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "driver/uart.h"
#include "soc/uart_struct.h"
#include "string.h"

extern "C" {
/**
  * UART Comms Code
  * Author: Patrick Scherer
  *
  * This code demonstrates how we can use interrupts to process UART input without blocking the CPU.
  */


//<editor-fold-begin> -------------------- GLOBALS -----------------------

// ESP_LOGI task shorthands - these don't necessarily have to be here,
// it just improves overall readability.
static const char *INIT_TAG = "UART_COMMS_INIT";
static const char *ISR_TAG = "UART_COMMS_ISR";

//ISR stuff
static intr_handle_t handle_console; // Required for interrupts
static QueueHandle_t xQueueRX = NULL;  //Non-static for inputProcessing.c
static QueueHandle_t xQueueTX = NULL;
static bool isCurrentlySending = false;

//Synchronization
static SemaphoreHandle_t xTxSemaphore = NULL; //UART output synchronization

//Other
static bool isEnabled = false;  //Global boolean to check if the uart is currently enabled.

//UART and GPIO configuration
 // UART buffer size, also used for FreeRTOS queues.
static const int BUF_SIZE = 256;

#define UART_NUMBER (UART_NUM_1)  // UART number that is used for communication. Some snippets are still hardcoded for "UART1"!

#define TXD_PIN (GPIO_NUM_25) // UART comms TX pin. Change from pin 4/5 since we're using 5 to address the display.

#define RXD_PIN (GPIO_NUM_26) // UART comms RX pin. Also changed from 12/13 since those seem to affect the JTAG debugger
// Note: Be careful when picking pins, 1-3 are reserved for UART0, 4-5 clash with the default display GPIO and 12-13 clash with the JTAG debugger.
// They can still be used but don't expect the overwritten functionality to work when you do

#define INCLUDE_vTaskSuspend 1 //This will block FreeRTOS Queue or Semaphore access indefinetly when using portMAX_DELAY as wait duration.

//<editor-fold-end> ---------------- GLOBALS --------------------------

//<editor-fold-begin> -------------- ISR -------------------------------

static void processRxFifo() {

/**
  * RX FIFO Interrupt Handling Subroutine
  *
  * Reads each byte from the RX FIFO queue and adds them to the
  * FreeRTOS RX queue for other tasks to use.
  * This routine only clears the RX FIFO FULL interrupt status when
  * the RX FIFO count returns zero, since its value might change
  * during ISR execution.
  */

  // pdFALSE means we have not woken a task at the start of the ISR.
  BaseType_t xHigherPriorityTaskWoken = pdFALSE;

  // FIFO current queue count, can change while IRS is working!
  uint16_t rx_fifo_length = UART1.status.rxfifo_cnt;

  if (rx_fifo_length == 0) {
    // Clear RXFIFO FULL interrupt status since it is now empty.
    uart_clear_intr_status(UART_NUMBER, UART_RXFIFO_FULL_INT_CLR);
  }
  for (int i = 0; i < rx_fifo_length; i++) {
    uint8_t currentChar = UART1.fifo.rw_byte;
    xQueueSendFromISR(xQueueRX, &currentChar, &xHigherPriorityTaskWoken);
  }
}

static void processTxDone(){

/**
  * TX Done Interrupt Handling Subroutine
  *
  * Checks the FreeRTOS RX queue if there are symbols to send and sends them.
  *
  * This process will only send characters up to the bufferSize count
  * before repeating.
  * This helps to avoid Interrupt WDT timeouts.
  */

  BaseType_t xTaskWokenByReceive = pdFALSE;
  isCurrentlySending = true;
  int bufferSize = 32;
  int totalCharacterCount = 0;
  if (uxQueueMessagesWaitingFromISR(xQueueTX) == 0) {
    isCurrentlySending = false;
    uart_clear_intr_status(UART_NUMBER, UART_TX_DONE_INT_CLR);
  } else {
    uint8_t charsToSend[bufferSize];
    for (int i = 0; i < bufferSize; i++) {
      uint8_t receivedChar;
      if(xQueueReceiveFromISR(xQueueTX, &receivedChar, &xTaskWokenByReceive) ==
         pdTRUE) {
        charsToSend[i] = receivedChar;
        totalCharacterCount++;
      } else {
        break;
      }
    }
    uart_write_bytes(UART_NUMBER,
                     (const char*) &charsToSend,
                     totalCharacterCount);
  }
}

static void processFrameOrParityError() {

/**
  * Party or Frame Error Interrupt Handling Subroutine
  *
  * For now, only flushes the RX FIFO and clears the interrupt status.
  * Expected input seems to arrive just fine while doing just that,
  * even with parity errors all over.
  */

  uart_flush(UART_NUMBER);
  uart_clear_intr_status(UART_NUMBER, UART_FRM_ERR_INT_CLR);
  uart_clear_intr_status(UART_NUMBER, UART_PARITY_ERR_INT_CLR);
  //uart_write_bytes(UART_NUMBER, (const char*) "--PRT/FRM--", 11); //Debugging purposes
}

static void IRAM_ATTR uart_irs(void *arg) {

/**
  * Custom UART Interrupt Service Routine
  *
  * Deals with RX FIFO FULL and TX DONE interrupts by calling tailored
  * subroutines.
  * This ISR loops until all there are no interrupts to deal with anymore.
  * The subroutines need to clear these interrupt flags themselves.
  *
  * Note: This is currently hardcoded for UART1
  */

  // Loop while there are still interrupts to deal with.
  while (UART1.int_st.val) {
    // Possible implementation for determining which interrupt was set.
    // Needs testing!
    if(UART1.int_st.val & UART_RXFIFO_FULL_INT_ST_M) {
      //RX FIFO Full interrupt is set
      processRxFifo();
    } else if (UART1.int_st.val & UART_TX_DONE_INT_ST_M) {
      //TX Done interrupt is set
      processTxDone();
    } else if ((UART1.int_st.val & UART_FRM_ERR_INT_ST_M) ||
               UART1.int_st.val & UART_PARITY_ERR_INT_ST_M) {
      //Frame or parity error interrupt is set
      processFrameOrParityError();
    } else {
      //Some other interrupt is set.
      ESP_LOGW(ISR_TAG, "Unexpected interrupt detected!");
    }
  }
}

//<editor-fold-end> --------------------- ISR ---------------------------

//<editor-fold-begin> ------------------ UART COMMS ---------------------

static void sendTxQueueFromTask(){

/**
  * UART Communications Send TX Queue From Task
  *
  * This method enters a critical region and sends characters
  * that are currently in the TX queue.
  * As the name implies, this method is designed to be used from tasks.
  * If it takes too long to fetch all characters before exiting the
  * critical region, an Interrupt WDT timeout may occur.
  * Thus we only send one character and let the TX Done ISR do the rest.
  */

  portMUX_TYPE mutex = portMUX_INITIALIZER_UNLOCKED;
  portENTER_CRITICAL(&mutex);
  if (uxQueueMessagesWaiting(xQueueTX) > 0) {
    uint8_t charToSend;
    if (xQueueReceive(xQueueTX, &charToSend, 0) == pdTRUE) {
      uart_write_bytes(UART_NUMBER, (const char*) &charToSend, 1);
    } else {
      ESP_LOGI("TX_QUEUE_SEND_FROM_TASK", 
               "Warning: Failed to receive an item from the queue,"
	       " nothing will be sent.");
    }
  }
  portEXIT_CRITICAL(&mutex);
}

void sendMultipleUartCharacters(uint8_t* charsToSend, uint8_t itemCount) {

/**
  * UART TX Send Multiple Uart Characters
  *
  * Takes multiple characters and prepares them for sending over the uart.
  * All items will be fed into the TX queue and depending on if there is
  * currently stuff being sent, it will start the send process.
  */

  //TODO: Prevent output during input (except input echo)
  //Sync to ensure longer strings don't get jumbled up.
  // Might wanna change later!
  xSemaphoreTake(xTxSemaphore, portMAX_DELAY);

  for (int i = 0; i < itemCount; i++) {
    xQueueSend(xQueueTX, &charsToSend[i], portMAX_DELAY);
  }

  xSemaphoreGive(xTxSemaphore);
  if (!isCurrentlySending) {
    sendTxQueueFromTask();
  }

  //TODO: Make adjustments to ensure current input-in-progress is maintained

}

void sendSingleUartCharacter(uint8_t charToSend) {

/**
  * UART TX Send Single Uart Character
  *
  * Convenience method for single character send.
  * Simply calls the function for multiple characters with the
  * appropirate parameters.
  */

  sendMultipleUartCharacters((uint8_t*) &charToSend, 1);
}

uint8_t receiveSingleUartCharacter() {

/**
  * UART RX Receive Single Uart Character
  *
  * Polls the queue for a single uart character. Waits indefinitely
  */

  uint8_t receivedChar;
  xQueueReceive(xQueueRX, &receivedChar, portMAX_DELAY);
  return receivedChar;
}

//<editor-fold-end> -------------------- UART COMMS ----------------------

//<editor-fold-begin> ----------------- INIT ------------------------------

static bool setupUart() {

/**
  * Uart Comms UART Setup Routine
  *
  * Prepares UART for use by applying the appropriate config,
  * registering the ISR and enabling appropriate interrupts.
  * Returns true when the function reaches its end.
  */

  // UART configuration for UART1 using the pins and buffer size
  // specified above.
  ESP_LOGI(INIT_TAG, "Configuring UART...");
  const uart_config_t uart_config = {
        .baud_rate = 115200,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE
    };
  ESP_ERROR_CHECK(uart_param_config(UART_NUMBER, &uart_config));
  ESP_ERROR_CHECK(uart_set_pin(UART_NUMBER, TXD_PIN, RXD_PIN,
                               UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE));

  // Driver installer note: UART, RX buffer, TX buffer, event queue size and
  //   handle, interrupt flags
  ESP_ERROR_CHECK(uart_driver_install(UART_NUMBER, BUF_SIZE * 2, 0, 0, 
                                      NULL, 0));
  // Setting up interrupt stuff
  ESP_ERROR_CHECK(uart_isr_free(UART_NUMBER));
  ESP_ERROR_CHECK(uart_isr_register(UART_NUMBER,uart_irs, NULL, 
                                    ESP_INTR_FLAG_IRAM, &handle_console));
/*  const uart_intr_config_t uart_intr = {
       			// Enables interrupts for rxfifo full
    .intr_enable_mask = UART_RXFIFO_FULL_INT_ENA_M 
                        // Enables interrupts for completed TX
      | UART_TX_DONE_INT_ENA_M
                        // Enables interrupts for received data frame errors
      | UART_FRM_ERR_INT_ENA_M 
                        // Enables interrupts for data parity error
      | UART_PARITY_ERR_INT_ENA_M,
                        // Sets the RX FIFO FULL threshold to 1,
                        // thus triggering the ISR on each received character.
    .rxfifo_full_thresh = 1
    };
*/
   uart_intr_config_t uart_intr ;
   uart_intr.intr_enable_mask =
       			// Enables interrupts for rxfifo full
               UART_RXFIFO_FULL_INT_ENA_M 
                        // Enables interrupts for completed TX
               | UART_TX_DONE_INT_ENA_M
                        // Enables interrupts for received data frame errors
               | UART_FRM_ERR_INT_ENA_M 
                        // Enables interrupts for data parity error
               | UART_PARITY_ERR_INT_ENA_M;
                        // Sets the RX FIFO FULL threshold to 1,
                        // thus triggering the ISR on each received character.
    uart_intr.rxfifo_full_thresh = 1;

  ESP_LOGI(INIT_TAG, "... 3");
  ESP_ERROR_CHECK(uart_intr_config(UART_NUMBER, &uart_intr));
  ESP_LOGI(INIT_TAG, "UART configured.");
  return true;
}

static bool setupQueuesAndSemaphores() {

/**
  * Uart Comms FreeRTOS Setup Function
  *
  * Prepares the RX and TX queues, as well as the TX sync semaphore.
  * Returns true when the function reaches its end. 
  * If an error occurs, returns false.
  */

  ESP_LOGI(INIT_TAG, "Preparing FreeRTOS queues...");
  // Queue length that equals the UART buffer size
  xQueueRX = xQueueCreate(BUF_SIZE, sizeof(uint8_t));
  if (xQueueRX == NULL) {
    ESP_LOGE(INIT_TAG, "Failed to initialize Uart FreeRTOS RX queue!");
    return false;
  }
  xQueueTX = xQueueCreate(BUF_SIZE, sizeof(uint8_t));
  if (xQueueTX == NULL) {
    ESP_LOGE(INIT_TAG, "Failed to initialize Uart FreeRTOS TX queue!");
    return false;
  }
  ESP_LOGI(INIT_TAG, "FreeRTOS queues prepared.");

  xTxSemaphore = xSemaphoreCreateBinary();
  if (xTxSemaphore == NULL) {
    ESP_LOGE(INIT_TAG, "Failed to initialize Uart FreeRTOX TX semaphore!");
    return false;
  } else {
    xSemaphoreGive(xTxSemaphore);
    ESP_LOGI(INIT_TAG, "FreeRTOS TX semaphore prepared.");
  }
  return true;
}


bool enableUartComms() {

/**
  * Uart Comms Enable Function
  *
  * Enables UART communication (after setup) by flushing the FIFO and
  * enabling interrupts.
  * This function is used to re-enable the UartComms after
  * temporarily disabling it using disableUartComms().
  */

  if(isEnabled){
    //ESP_LOGI("UART_COMMS_SETUP", "UartComms is already enabled."); //Remove this later if it gets spammy
  } else {
    uart_flush(UART_NUMBER);
    ESP_ERROR_CHECK(uart_enable_intr_mask(UART_NUMBER,
        UART_RXFIFO_FULL_INT_ENA_M         // Interrupts for rxfifo full
        | UART_TX_DONE_INT_ENA_M           // Interrupts for completed TX
        | UART_FRM_ERR_INT_ENA_M           // Interrupts for received data
                                           //  frame errors
        | UART_PARITY_ERR_INT_ENA_M));     // Interrupts for data parity error
    isEnabled = true;
    ESP_LOGI("UART_COMMS_SETUP", "UartComms successfully enabled!");
  }
  return true;
}

bool disableUartComms() {
  /**
    * Uart Comms Disable Function
    *
    * Disables UART communication (after setup) by simply disabling interrupts.
    * This function is used to temporarily disable uartComms and can be
    * undone by calling enableUartComms().
    */

    if(isEnabled){
      ESP_ERROR_CHECK(uart_disable_intr_mask(UART_NUMBER,
        UART_RXFIFO_FULL_INT_ENA_M       // Interrupts for rxfifo full
          | UART_TX_DONE_INT_ENA_M       // Interrupts for completed TX
          | UART_FRM_ERR_INT_ENA_M       // Interrupts for received data
                                         //  frame errors
          | UART_PARITY_ERR_INT_ENA_M)); // Interrupts for data parity error
      isEnabled = false;
      ESP_LOGI("UART_COMMS_SETUP", "UartComms successfully disabled!");
    } else {
      ESP_LOGI("UART_COMMS_SETUP", "UartComms is already disabled!");
    }
    return true;
}

bool setupUartComms() {

/**
  * UART Comms Initialization
  *
  * Initializes all necessary functionality for UART Comms.
  * First the uart itself is configured by calling the appropriate subroutine,
  * then Queues and Semaphores are prepared.
  * This function only returns true when everything initialized successfully.
  * Otherwise false is returned.
  */

  if (!setupUart()) {
    ESP_LOGE(INIT_TAG, "Uart initialization failed!");
    //TODO: Cleanup Uart stuff
    return false;
  }

  if(!setupQueuesAndSemaphores()) {
    ESP_LOGE(INIT_TAG, "Uart FreeRTOS setup failed!");
    //TODO: Cleanup FreeRTOS stuff
    return false;
  }

  isEnabled = true;
  //enableUartComms();
  return true;
}

//<editor-fold-end> ------------------ INIT ----------------------
}


//Implementation work-in-progress, may function in its current
// state but is not guaranteed.

namespace pearlrt {

  // set default values
  bool Esp32Uart::doNewLineTranslation = false;
  bool Esp32Uart::setupComplete = false;

  Esp32Uart::Esp32Uart(int _port, int baudRate,
         int bitsPerCharacter, int stopBits, char* parity, bool xon) {

    //Due to dationRead() not returning when there is no UART input,
    // we'll seperate IO mutexes
    //devMutex serves as a whole-device mutex to ensure
    // no device access happens twice.
    inMutex.name("Esp32UartCommsDriverIn");
    outMutex.name("Esp32UartCommsDriverOut");
    devMutex.name("Esp32UartCommsDriver");

    openUserDationsCount = 0;
    hasUnGetChar = false;
    doNewLineTranslation = false;
    setupComplete = false;

    if (!setupUartComms()) {
      Log::error("Esp32UartCommsDriver: could not initialize uartComms");
      throw theInternalDationSignal;
    }

    setupComplete = true;
  }

  Esp32Uart* Esp32Uart::dationOpen(const char * idf,
                                   int openParam) {

    if (openParam & (Dation::IDF | Dation::CAN)) {
       Log::error("Esp32Uart: does not support IDF and CAN");
       throw theDationParamSignal;
    }

    devMutex.lock();

    if (!setupComplete) {
       Log::error("Esp32Uart: setup not completed");
       devMutex.unlock();
       throw theOpenFailedSignal;
    }

    if (openUserDationsCount == 0) {
      enableUartComms();
    }

    openUserDationsCount++;

    devMutex.unlock();

    return this;

  }

  void Esp32Uart::dationClose(int closeParam) {

    devMutex.lock();

    if (openUserDationsCount == 0) {
       Log::error("Esp32Uart: no dation opened");
       devMutex.unlock();
       throw theCloseFailedSignal;
    }

    if (closeParam & Dation::CAN) {
       Log::error("Esp32Uart: CAN not supported");
       devMutex.unlock();
       throw theDationParamSignal;
    }

    openUserDationsCount--;

    if (openUserDationsCount == 0) {
      disableUartComms();
    }

    devMutex.unlock();
  }

  void Esp32Uart::dationRead(void * destination, size_t size) {

    devMutex.lock();
    char* dataPointer = (char*) destination;

    if (openUserDationsCount == 0) {
       Log::error("Esp32Uart: not opened");
       devMutex.unlock();
       throw theDationNotOpenSignal;
    }

    if (!setupComplete) {
       Log::error("Esp32Uart: setup incomplete");
       devMutex.unlock();
       throw theReadingFailedSignal;
    }

    if (hasUnGetChar) {
       hasUnGetChar = false;
       *dataPointer = unGetChar;
       size--;
       dataPointer++;
    }

    //Access input mutex before releasing device mutex.
    // Ensure we have access to the IO first
    inMutex.lock(); 
    devMutex.unlock();


    for (int receivedCharacterCount = 0;
             receivedCharacterCount < size;
             receivedCharacterCount++) {

      //This function returns an uint8_t so casting should be fine
      *dataPointer = (char)receiveSingleUartCharacter();

      dataPointer++;
    }

    inMutex.unlock();

  }

  void Esp32Uart::dationWrite(void * destination, size_t size) {

    devMutex.lock();

    char* dataPointer = (char*) destination;

    if (openUserDationsCount == 0) {
       Log::error("Esp32Uart: not opened");
       devMutex.unlock();
       throw theDationNotOpenSignal;
    }

    if (!setupComplete) {
       Log::error("Esp32Uart: setup incomplete");
       devMutex.unlock();
       throw theReadingFailedSignal;
    }


    uint8_t charsToTransfer[size];
    for (int charsTransferred = 0;
         charsTransferred < size;
         charsTransferred++) {
      charsToTransfer[charsTransferred] = (uint8_t) *dataPointer;
      dataPointer++;
    }

    outMutex.lock();
    devMutex.unlock();

    sendMultipleUartCharacters(charsToTransfer, (uint8_t) size);

    outMutex.unlock();

  }

  void Esp32Uart::dationUnGetChar(const char c) {

    devMutex.lock();

    if (openUserDationsCount == 0) {
       Log::error("Esp32Uart: not opened");
       devMutex.unlock();
       throw theDationNotOpenSignal;
    }

    if (!setupComplete) {
       Log::error("Esp32Uart: setup incomplete");
       devMutex.unlock();
       throw theReadingFailedSignal;
    }


    hasUnGetChar = true;
    unGetChar = c;

    devMutex.unlock();

  }

  int Esp32Uart::capabilities() {
    return (Dation::FORWARD | Dation::IN | Dation::PRM | Dation::ANY | Dation::OUT | Dation::INOUT);
  }

  void Esp32Uart::translateNewLine(bool doNewLineTranslation) {
    this->doNewLineTranslation = doNewLineTranslation;
  }


  //The following is only included due to esp-idf compiler
  // complaints (pure virtual functions from Dation)
  void Esp32Uart::suspend() {
    Log::error("Esp32Uart: cannot suspend");
    throw theDationNotSupportedSignal;
  }

  void Esp32Uart::terminate() {
    Log::error("Esp32Uart: cannot terminate");
    throw theDationNotSupportedSignal;
  }

}
