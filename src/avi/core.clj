(ns avi.core
  (:import [avi.terminal Terminal])
  (:require [packthread.core :refer :all]
            [avi.editor :as e]
            [avi.command-line-mode]
            [avi.normal-mode]
            [avi.insert-mode]
            [avi.render :as render]
            [avi.world :refer :all])
  (:gen-class))

(defn- event-stream
  ([world]
   (event-stream world (terminal-size world)))
  ([world current-size]
   (lazy-seq
     (let [keystroke (read-key world)
           new-size (terminal-size world)]
       (cond->> (event-stream world new-size)
         true
         (cons [:keystroke keystroke])

         (not= current-size new-size)
         (cons [:resize new-size]))))))

(defn- editor-stream
  [world args]
  (let [initial-editor (e/initial-editor (terminal-size world) args)]
    (->> (event-stream world)
         (reductions e/safe-respond initial-editor)
         (take-while (complement e/finished?)))))

(defn- perform-effects!
  [editor]
  (when (:beep? editor)
    (beep *world*))
  (update-terminal *world* (render/render editor)))

(defn- run
  [world args]
  (binding [*world* world]
    (setup *world*)
    (doseq [editor (editor-stream *world* args)]
      (perform-effects! editor))
    (cleanup *world*)))

(defn -main
  [& args]
  (let [world (reify
                World
                (setup [_] (Terminal/start))
                (cleanup [_] (Terminal/stop))
                (read-key [_] (Terminal/getKey))
                (beep [_] (Terminal/beep))
                (terminal-size [_]
                  (let [size (Terminal/size)]
                    [(get size 0) (get size 1)]))
                (update-terminal [_ {chars :chars,
                                     attrs :attrs,
                                     width :width,
                                     [i j] :cursor}]
                  (Terminal/refresh i j width chars attrs))
                (read-file [_ filename]
                  (slurp filename))
                (write-file [_ filename contents]
                  (spit filename contents)))]
    (run world args)))
