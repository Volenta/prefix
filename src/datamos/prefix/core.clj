(ns datamos.prefix.core
  "Prefix URI mapping for dataMos platform

  base URI models in dataMos:

  For prefix :datamos-id it's:
  http:// {domain} / {category} / {type} / {concept} + {check} + {reference} (note the '+'(plus-sign) between concept, check and reference) .

  For the others (:dms-def :datamos-cfg :dmscfg-def :datamos-fn :dmsfn-def) it's:
  http:// {domain} / {category} / {type} / {name}


  domain    = the domain name for the specific URI. Eg: datamos.org
  category  = the category the data referenced by the URI belongs to. Currently this is one of:
                data - basically all URI's belong in this category except for the ones below:
                config - URI's which define configuration of datamos or components.
                function - URI's which refer to a function as supplied by a component
  type      = is the type of resouce the URI references to. Could be one of the following values:
                id - for instances
                def - for definitions

  concept   = The kind of concept the URI is refering to
  check     = Abbreviated prefix, to make a visual check between prefix and entity possible.
                Supply when {type} = id. Otherwise leave it out.
  reference = The unique id, to refer to. Supply when {type} = id. Otherwise leave it out.
  or
  name      = The unique name for a thing.

  Prefixes refer to URI's up until the last slash ('/') So prefixes only refer to:
  http:// {domain} / {category} / {type} /"
  (:require [datamos
             [core :as dc]
             [communication :as dcom]
             [base :as base]
             [sign-up :as sup]
             [module-helpers :as hlp]
             [rdf-function :as rdf-fn]
             [messaging :as dm]]
            [mount.core :as mnt :refer [defstate stop start]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def remote-components (atom {}))

(def known-prefixes
  {:datamos-id  "http://ld.datamos.org/data/id/"
   :dms-def     "http://ld.datamos.org/data/def/"
   :datamos-cfg "http://ld.datamos.org/config/id/"
   :dmscfg-def  "http://ld.datamos.org/config/def/"
   :datamos-fn  "http://ld.datamos.org/function/id/"
   :dmsfn-def   "http://ld.datamos.org/function/def/"
   :datamos-var "http://ld.datamos.org/variable/id/"
   :datamos-qry "http://ld.datamos.org/query/id/"
   :dmsqry-def  "http://ld.datamos.org/query/def/"
   :rdfs        "http://www.w3.org/2000/01/rdf-schema#"
   :rdf         "http://www.w3.org/1999/02/22-rdf-syntax-ns#"})

(def ^:private prefixes (atom {}))

(def ^:private message-header-generic-subjects-set #{{} :dms-def/message})

(defn local-register
  []
  @remote-components)

(defn prefix-response
  [msg-ref msg-hdr prefix-map]
  (let [[rcpt] (filter
                 (fn [k] ((set (vals (k msg-hdr))) :dms-def/sender))
                 (remove message-header-generic-subjects-set (keys msg-hdr)))
        m-id (:dms-def/message-id
               (rdf-fn/get-predicate-object-map msg-ref))
        content {m-id {:dms-def/has-prefixes prefix-map}}]
    (log/debug "@prefix-response" msg-ref msg-hdr prefix-map rcpt m-id content)
    (log/trace "@prefix-response" (log/get-env))
    (dcom/speak dcom/speak-connection dm/exchange base/component rcpt :dmsfn-def/module-id :datamos-fn/prefix-list content)))

(defn match-prefix
  [_ _ message]
  (let [m-cnt (rdf-fn/message-content message)
        m-hdr (rdf-fn/message-header message)
        namespaces (:dms-def/namespaces
                     (rdf-fn/get-predicate-object-map
                       (rdf-fn/message-content message)))
        prefix-map (select-keys
                     @prefixes namespaces)
        msg-ref (rdf-fn/predicate-filter m-cnt #{:dms-def/message-id})]
    (log/debug "@match-prefix" message m-cnt m-hdr namespaces)
    (log/trace "@match-prefix" (log/get-env))
    (prefix-response msg-ref m-hdr prefix-map)))


(def component-fns (merge {:datamos-fn/match-prefix datamos.prefix.core/match-prefix}
                          (hlp/local-module-register remote-components)))

(base/component-function {:dmsfn-def/module-type :dmsfn-def/core
                          :dmsfn-def/module-name :dmsfn-def/prefix
                          :datamos/local-register (datamos.prefix.core/local-register)
                          :dms-def/provides       datamos.prefix.core/component-fns})

(defn go
  []
  (do
    (log/merge-config!
      {:appenders
       {:println {:min-level :info}
        :spit (merge (appenders/spit-appender {:fname "log/datamos.log"})
                     {:min-level :trace})}}))
  (reset! prefixes known-prefixes)
  (log/info "@go - Starting dataMos - by Prefix Module")
  (start)
  (log/info "@go - dataMos Running - by Prefix Module"))

(defn stp
  []
  (do
    (stop)
    (log/info "@stop - dataMos has stopped")))

(defn reset
  []
  (do
    (stp)
    (refresh :after 'datamos.prefix.core/go)))

(defn -main
  "Initializes datamos.core. Configures the exchange"
  [& args]
  (reset))
