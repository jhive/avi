(ns avi.test-helpers
  (:import [java.io FileNotFoundException])
  (:require [avi.core]
            [avi.editor :as e]
            [avi.eventmap :as em]
            [avi.render :as render]
            [avi.world :refer :all]
            [clojure.string :as string]))

(def ten-lines
  (str "One\nTwo\nThree\nFour\nFive\nSix\n"
       "Seven\nEight\nNine\nTen"))

(defn- event
  [event-name]
  (let [event-type (if (.startsWith event-name "<Resize ")
                     :resize
                     :keystroke)
        event-data (if (= :resize event-type)
                     (read-string (apply str (drop 1 (drop-while #(not= % \space) event-name))))
                     event-name)]
    [event-type event-data]))

(defn- make-events
  [string-of-commands]
  (->> string-of-commands
       (em/split-string-of-commands)
       (map event)))

(defn- simulate
  [& {width :width,
      file-contents :editing,
      string-of-commands :after,
      :or {width 20,
           file-contents "One\nTwo\nThree\n.",
           keystrokes ""}}]
  (let [events (make-events string-of-commands)
        start-args (if (= :nothing file-contents)
                     []
                     ["test.txt"])
        file-written (atom nil)
        test-world (reify
                     World
                     (read-file [_ filename]
                       (cond
                         (= :not-found file-contents)
                         (throw (FileNotFoundException. "not found"))

                         (= "test.txt" filename)
                         file-contents))
                     (write-file [_ filename content]
                       (swap! file-written (constantly [filename content]))
                       nil))
        initial-editor (binding [*world* test-world]
                         (e/initial-editor [8 width] start-args))
        final-editor (binding [*world* test-world]
                       (reduce
                         e/respond
                         initial-editor
                         events))]
    {:editor final-editor
     :file-written @file-written}))

(defn file-written
  [& args]
  (:file-written (apply simulate args)))

(defn editor
  [& args]
  (:editor (apply simulate args)))

(defn terminal
  [& args]
  (let [{width :width,
         chars :chars,
         attrs :attrs} (render/render (apply editor args))
        height (quot (count chars) width)
        lines (map
                #(String. chars (* % width) width)
                (range height))
        line-annotations (->> (range height)
                              (map (fn [i]
                                     (get attrs (* i width))))
                              (map render/attr-description))]
    (->> (interleave lines line-annotations)
         flatten)))

(defn message-line
  [& {:as arguments}]
  (let [terminal-args (->> arguments
                           (merge {:width 120})
                           (apply concat))]
    (-> (apply terminal terminal-args)
        last
        string/trimr)))

(defn cursor
  [& args]
  (:cursor (render/render (apply editor args))))

(defn beeped
  [editor]
  (:beep? editor))

(defn did-not-beep
  [editor]
  (not (:beep? editor)))
