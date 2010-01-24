(ns ccw.editors.antlrbased.PareditAutoEditStrategy
  (:use [paredit [core :only [paredit]]])  
  (:import
    [org.eclipse.jface.text IAutoEditStrategy
                            IDocument
                            DocumentCommand])
  (:gen-class
   :implements [org.eclipse.jface.text.IAutoEditStrategy]
   :init init
   :state state))
   
(defn- -init
  [] [[] (ref {})])   

; TODO move this into paredit itself ...
(def *one-char-command* 
  {"(" :paredit-open-round 
   "[" :paredit-open-square
   "{" :paredit-open-curly })

(defn -customizeDocumentCommand 
  [#^IAutoEditStrategy this, #^IDocument document, #^DocumentCommand command]
  (when (and (.doit command)
             (= 0 (.length command))
             (contains? *one-char-command* (.text command)))
    (let [result (paredit (get *one-char-command* (.text command))
                          {:text (.get document) 
                           :offset (.offset command) 
                           :length 0})]
      (when (not= :ko (-> result :parser-state))
        (set! (.offset command) (-> result :modifs first :offset))
        (set! (.length command) (-> result :modifs first :length))
        (set! (.text command) (-> result :modifs first :text))
        (set! (.shiftsCaret command) false)
        (set! (.caretOffset command) (:offset result))))))