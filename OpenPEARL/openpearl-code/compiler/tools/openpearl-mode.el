;;; openpearl-mode.el --- Major mode for editing OpenPEARL source code files.

;; Copyright (c) 2016, Marcel Schaible

;; Author: Marcel Schaible ( marcel.schaible (at) fernuni-hagen.de )
;; Version: 1.0.0
;; Created: 20 Jan 2016
;; Keywords: languages
;; Homepage: 

;; This file is not part of GNU Emacs.

;;; License:

;; You can redistribute this program and/or modify it under the terms
;; of the GNU General Public License version 3.

;;; Commentary:

;; short description here

;; full doc on how to use here

;;; Code:

;; sample major mode with its own keymap

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require 'smie)
(require 'compile)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun jea-compile-project-1 ()
  "build project 1"
  (interactive)
  (message "jea-compile-project-1")	
  (let ((buf-name '"*jea-compile-project1*")
        (working-dir '"~/code"))
    (save-excursion
      (with-current-buffer (get-buffer-create buf-name)
        (barf-if-buffer-read-only)
        (erase-buffer))
      (cd working-dir)
      (call-process-shell-command "pwd" nil buf-name 't)
      (call-process-shell-command "gcc -c hello.c" nil buf-name 't)
      (call-process-shell-command "gcc hello.o" nil buf-name 't)
      (call-process-shell-command "./a.out" nil buf-name 't)
      (message "compile project 1 done")
      )))
      
;; (global-set-key [(f7)] 'jea-compile-project-1)

;; ----------------------------------------
;; commands

(defun openpearl-cmd1 ()
  "do something"
  (interactive)
  (message "cmd1 called")
  (jea-compile-project-1))

(defun openpearl-cmd2 ()
  "do something"
  (interactive)
  (message "cmd2 called"))

(defun openpearl-cmd3 ()
  "do something"
  (interactive)
  (message "cmd3 called"))

(defun openpearl-cmd4 ()
  "do something"
  (interactive)
  (message "cmd4 called"))

;; ----------------------------------------
;; keybinding

(defvar openpearl-map nil "Keymap for `openpearl-mode'")
;; make sure that the var name is your mode name followed by -map. That way, define-derived-mode will automatically set it as local map

;; also, by convention, variable names for keymap should end in -map

(progn
  (setq openpearl-map (make-sparse-keymap))

  (define-key openpearl-map (kbd "C-c C-a") 'openpearl-cmd1)

  (define-key openpearl-map (kbd "C-c C-b") 'openpearl-cmd2)

  (define-key openpearl-map (kbd "C-c C-c") 'openpearl-cmd3)

  (define-key openpearl-map (kbd "C-c C-d") 'openpearl-cmd4)

  ;; by convention, major mode's keys should begin with the form C-c C-‹key›
  ;; by convention, keys of the form C-c ‹letter› are reserved for user. don't define such keys in your major mode
  )

;; ----------------------------------------
;; define the mode

(define-derived-mode my-mode prog-mode "my"
  "my-mode is a major mode for editing language my.

\\{openpearl-map}"

  ;; actually no need
  (use-local-map openpearl-map) ; if your keymap name is modename follow by -map, then this line is not necessary, because define-derived-mode will find it and set it for you

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; define several category of keywords
(setq openpearl-keywords '( "A" "ACTIVATE" "ALL" "ALPHIC" "ALT"
			    "BASIC" "BEGIN" "BY"
			    "CASE" "CLOSE" "CONTINUE" "CREATED" "CYCLIC"
			    "DCL" "DECLARE" "DIM" "DIRECT" "DISABLE" "DURING"
			    "ELSE" "ENABLE" "END" "EXIT"
			    "FIN" "FOR" "FORWARD" "FROM"
			    "GLOBAL" "GOTO"
			    "HRS"
			    "IF" "INDUCE" "INIT" "INTERRUPT" "INV"
			    "MAIN" "MIN" "MODULE" "MODEND"
			    "OPEN" "ON" "OUT" 
			    "PRESET" "PRIO" "PROBLEM" "PROC" "PROCEDURE" "PUT"
			    "RELEASE" "REPEAT" "REQUEST" "RETURN" "RETURNS" "RST"
			    "SEC" "SEND" "SIGNAL" "SKIP" "SPC" "SPECIFY" "STRUCT" "SYSTEM"
			    "TAKE" "TASK" "TERMINATE" "THEN" "TO" "TRIGGER" "TRY"
			    "WHEN" "WHILE"
			    ))

(setq openpearl-types '("BIT" "CHAR" "CHARACTER" "CLOCK" "DATE" "DATION" "DUR" "DURATION" "FIXED" "FLOAT" "SEMA" "TIME"))

(setq openpearl-constants '())

(setq openpearl-events '())

(setq openpearl-functions '( "ABS"
			     "COS"
			     "FIT"
			     "REM"
			     "SIGN" "SIN" "SIZEOF"
			     "TOFIXED" "TOFLOAT"
		      ))

;; generate regex string for each category of keywords
(setq openpearl-keywords-regexp (regexp-opt openpearl-keywords 'words))
(setq openpearl-type-regexp (regexp-opt openpearl-types 'words))
(setq openpearl-constant-regexp (regexp-opt openpearl-constants 'words))
(setq openpearl-event-regexp (regexp-opt openpearl-events 'words))
(setq openpearl-functions-regexp (regexp-opt openpearl-functions 'words))

;; create the list for font-lock.
;; each category of keyword is given a particular face
(setq openpearl-font-lock-keywords
      `(
        (,openpearl-type-regexp . font-lock-type-face)
        (,openpearl-constant-regexp . font-lock-constant-face)
        (,openpearl-event-regexp . font-lock-builtin-face)
        (,openpearl-functions-regexp . font-lock-function-name-face)
        (,openpearl-keywords-regexp . font-lock-keyword-face)
        ;; note: order above matters, because once colored, that part won't change.
        ;; in general, longer words first
        ))

;;;###autoload
(define-derived-mode openpearl-mode fundamental-mode
  "OpenPEARL mode"
  "Major mode for editing OpenPEARL"

  ;; code for syntax highlighting
  (setq font-lock-defaults '((openpearl-font-lock-keywords))))

;; clear memory. no longer needed
(setq openpearl-keywords nil)
(setq openpearl-types nil)
(setq openpearl-constants nil)
(setq openpearl-events nil)
(setq openpearl-functions nil)

;; clear memory. no longer needed
(setq openpearl-keywords-regexp nil)
(setq openpearl-types-regexp nil)
(setq openpearl-constants-regexp nil)
(setq openpearl-events-regexp nil)
(setq openpearl-functions-regexp nil)


;; add the mode to the `features' list
(provide 'openpearl-mode)

;;
(add-hook 'openpearl-mode-hook
          (lambda () (local-set-key (kbd "C-c C-c") #'prl)))


;;
;;
;;
(define-skeleton openpearl-procedure
  "No doc."
  "Name: "
  \n "PROCEDURE " str " (" (read-string "Arguments: ") ")"
  (let ((args (read-string "Result Type: ")))
    (if (not (equal args "")) (concat " : " args)))
  ";" > \n "BEGIN" > \n _ \n "END " str ";" > \n)

;;
;;
;;
(defcustom openpearl-compile-command "prl"
  "Command to compile OpenPEARL programs."
  :type 'string)

;;
;;
;;
(defvar openpearl-mode-map
  (let ((map (make-sparse-keymap)))
      (define-key map "\C-cp" 'openpearl-procedure)
      (define-key map "\C-c\C-c" 'openpearl-compile)
    map)
  "Keymap used in OpenPEARL mode.")

;;
;;
;;
(defun openpearl-compile ()
  (interactive)
  (message (concat openpearl-compile-command " " (buffer-name)))
  (compile (concat openpearl-compile-command " " (buffer-name))))

(add-hook 'openpearl-mode-hook
          (lambda ()
            (set (make-local-variable 'compile-command)
                 (concat "prl " buffer-file-name))))

;; Local Variables:
;; coding: utf-8
;; End:

;;; openpearl-mode.el ends here
