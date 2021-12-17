(defproject magnet/object-storage.s3 "0.6.8"
  :description "A Duct library for managing AWS S3 objects"
  :url "https://github.com/magnetcoop/object-storage.s3"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.9.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [amazonica "0.3.143" :exclusions [com.amazonaws/aws-java-sdk
                                                   com.amazonaws/amazon-kinesis-client
                                                   com.amazonaws/dynamodb-streams-kinesis-adapter]]
                 [com.amazonaws/aws-java-sdk-core "1.11.586"]
                 [com.amazonaws/aws-java-sdk-s3 "1.11.586"]
                 [integrant "0.7.0"]
                 [magnet/object-storage.core "0.1.3"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]]
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}}
   :profiles/dev {}
   :project/dev {:dependencies [[digest "1.4.8"]
                                [http-kit "2.5.3"]]
                 :plugins [[jonase/eastwood "0.3.4"]
                           [lein-cljfmt "0.6.2"]]}})
