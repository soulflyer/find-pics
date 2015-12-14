(defproject find-pics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [seesaw "1.4.5"]
                 [find-images "0.1.0-SNAPSHOT"]
                 [com.novemberain/monger "3.0.1"]
                 [org.clojure/tools.cli "0.3.3"]]
  :main find-pics.core
  :bin {:name "find-pics"
        :bin-path "~/bin"})
