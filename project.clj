(defproject magnet/object-storage.s3 "0.4.0-SNAPSHOT"
  :description "A Duct library for managing AWS S3 objects"
  :url "https://github.com/magnetcoop/object-storage.s3"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.8.3"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [amazonica "0.3.136" :exclusions [com.amazonaws/aws-java-sdk
                                                   com.amazonaws/amazon-kinesis-client
                                                   com.amazonaws/dynamodb-streams-kinesis-adapter]]
                 [com.amazonaws/aws-java-sdk-core "1.11.468"]
                 [com.amazonaws/aws-java-sdk-s3 "1.11.468"]
                 [integrant "0.7.0"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]]
  :profiles
  {:dev {:dependencies [[digest "1.4.8"]]
         :plugins [[jonase/eastwood "0.3.4"]
                   [lein-cljfmt "0.6.2"]]}
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}
          :plugins [[cider/cider-nrepl "0.18.0"]]}})
