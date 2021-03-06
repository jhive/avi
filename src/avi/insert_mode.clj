(ns avi.insert-mode
  (:require [packthread.core :refer :all]
            [avi.editor :as e]
            [avi.buffer :as b]
            [avi.eventmap :as em]
            [avi.pervasive :refer :all]))

(defn- key->text
  [key]
  (if (= key "<Enter>")
    "\n"
    key))

(defn- update-buffer-for-insert-event
  [editor [event-type event-data :as event]]
  (when-not (= event-type :keystroke)
    (fail :beep))
  (+> editor
    (in e/current-buffer
        (if (= event-data "<BS>")
          (if (= [0 0] (:cursor (e/current-buffer editor)))
            (fail :beep)
            b/backspace)
          (b/insert-text (key->text event-data))))))

(defn- play-script
  [editor script]
  (reduce
    update-buffer-for-insert-event
    editor
    script))

(defn- play-script-repeat-count-times
  [editor]
  (let [{script :script,
         repeat-count :count} (:insert-mode-state editor)]
    (reduce
      (fn [editor n]
        (play-script editor script))
      editor
      (range (dec repeat-count)))))

(def eventmap
  (em/eventmap
    ("<Esc>"
      [editor]
      (+> editor
          play-script-repeat-count-times
          (dissoc :insert-mode-state)
          (let [b (e/current-buffer editor)
                [i j] (:cursor b)
                new-j (max (dec j) 0)]
            (in e/current-buffer
                (b/move-cursor [i new-j] new-j)
                b/commit))
          (e/enter-mode :normal)))

    (:else
      [editor event]
      (update-buffer-for-insert-event editor event))))

(defn- with-event-recorded
  [editor event]
  (cond-> editor
    (not= event [:keystroke "<Esc>"])
    (update-in [:insert-mode-state :script] conj event)))

(defmethod e/respond :insert
  [editor event]
  (em/invoke-event-handler eventmap
                           (with-event-recorded editor event)
                           event))

(defmethod e/enter-mode :insert
  [editor mode & {script :script-prefix
                  :or {script []}}]
  (+> editor
      (assoc :mode :insert,
             :message [:white :black "--INSERT--"]
             :insert-mode-state {:count (or (:count editor) 1)
                                 :script script})
      (in e/current-buffer
          b/start-transaction)))
