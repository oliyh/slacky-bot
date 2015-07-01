(defproject slacky "0.1.0-SNAPSHOT"
  :description "Slacky - all the memes!"
  :url "https://github.com/oliyh/slacky"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [aleph "0.4.0"]
                 [clj-http "1.1.2"]
                 [cheshire "5.4.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [clj-slack-client "0.1.2-SNAPSHOT"]]
  :main ^:skip-aot slacky.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
