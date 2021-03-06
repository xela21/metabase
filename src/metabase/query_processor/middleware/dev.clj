(ns metabase.query-processor.middleware.dev
  "Middleware that's only active in dev and test scenarios. These middleware functions do additional checks
   of query processor behavior that are undesirable in normal production use."
  (:require [metabase
             [config :as config]
             [util :as u]]
            [schema.core :as s]))

;; The following are just assertions that check the behavior of the QP. It doesn't make sense to run them on prod because at best they
;; just waste CPU cycles and at worst cause a query to fail when it would otherwise succeed.

(def QPResultsFormat
  "Schema for the expected format of results returned by a query processor."
  {:columns               [(s/cond-pre s/Keyword s/Str)]
   (s/optional-key :cols) [{s/Keyword s/Any}]            ; This is optional because QPs don't neccesarily have to add it themselves; annotate will take care of that
   :rows                  [[s/Any]]
   s/Keyword              s/Any})

(def ^{:arglists '([results])} validate-results
  "Validate that the RESULTS of executing a query match the `QPResultsFormat` schema.
   Throws an `Exception` if they are not; returns RESULTS as-is if they are."
  (s/validator QPResultsFormat))

(def ^{:arglists '([qp])} check-results-format
  "Make sure the results of a QP execution are in the expected format.
   This takes place *after* the 'annotation' stage of post-processing.
   This check is skipped in prod to avoid wasting CPU cycles."
  (if config/is-prod?
    identity
    (fn [qp]
      (comp validate-results qp))))


(def ^{:arglists '([qp])} guard-multiple-calls
  "Throw an exception if a QP function accidentally calls (QP QUERY) more than once.
   This test is skipped in prod to avoid wasting CPU cycles."
  (if config/is-prod?
    identity
    (fn [qp]
      (comp qp (let [called? (atom false)]
                 (fn [query]
                   (u/prog1 query
                     (assert (not @called?) "(QP QUERY) IS BEING CALLED MORE THAN ONCE!")
                     (reset! called? true))))))))
