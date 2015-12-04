(ns find-pics.core
  (:use seesaw.core)
  (:gen-class))

(defn -main [& args]
  (invoke-later
   (-> (frame :title "Hello",
              :content "Hello, Seesaw",
              :on-close :exit)
       pack!
       show!)))
