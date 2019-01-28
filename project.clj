(defproject magnet/object-storage.s3 "0.2.0"
  :description "A Duct library for managing AWS S3 objects"
  :url "https://github.com/magnetcoop/object-storage.s3"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :dependencies [[amazonica "0.3.136" :exclusions [com.amazonaws/aws-java-sdk
                                                   com.amazonaws/amazon-kinesis-client
                                                   com.amazonaws/dynamodb-streams-kinesis-adapter]]
                 [com.amazonaws/aws-java-sdk-core "1.11.468"]
                 [com.amazonaws/aws-java-sdk-s3 "1.11.468"]
                 [integrant "0.7.0"]
                 [org.clojure/clojure "1.9.0"]]
  :profiles
  {:dev {:dependencies [[digest "1.4.8"]]
         :plugins [[jonase/eastwood "0.3.4"]
                   [lein-cljfmt "0.6.2"]]}
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}
          :plugins [[cider/cider-nrepl "0.18.0"]]}})
