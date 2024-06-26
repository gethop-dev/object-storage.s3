(defproject dev.gethop/object-storage.s3 "0.7.4-SNAPSHOT"
  :description "A Duct library for managing AWS S3 objects"
  :url "https://github.com/gethop-dev/object-storage.s3"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.9.8"
  :dependencies [[org.clojure/clojure "1.11.0"]
                 [amazonica "0.3.167" :exclusions [com.amazonaws/aws-java-sdk
                                                   com.amazonaws/amazon-kinesis-client
                                                   com.amazonaws/dynamodb-streams-kinesis-adapter]]
                 [com.amazonaws/aws-java-sdk-core "1.12.712"]
                 [com.amazonaws/aws-java-sdk-s3 "1.12.712"]
                 [javax.xml.bind/jaxb-api "2.3.1"]
                 [integrant/integrant "0.8.0"]
                 [dev.gethop/object-storage.core "0.1.5"]
                 [lambdaisland/uri "1.19.155"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]]
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}}
   :profiles/dev {}
   :project/dev {:dependencies [[digest/digest "1.4.10"]
                                [http-kit/http-kit "2.8.0"]]
                 :plugins [[jonase/eastwood "1.4.2"]
                           [lein-cljfmt/lein-cljfmt "0.9.2"]]
                 :eastwood {:linters [:all]
                            :ignored-faults {:unused-namespaces {dev.gethop.object-storage.s3-test true}
                                             :keyword-typos {dev.gethop.object-storage.s3 true}}
                            :debug [:progress :time]}}})
