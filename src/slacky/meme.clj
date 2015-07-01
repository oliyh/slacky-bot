(ns slacky.meme
  (:require [aleph.http :as http]
            [cheshire.core :as json]
            [clj-slack-client
             [rtm-transmit :as tx]]))

(def memecaptain-url "http://memecaptain.com/")

(defn- resolve-template-id [term]
  ) ;; use google for this, or pre-baked ones, or let user add some

(defn- create-instance [template-url text-upper text-lower]

  (let [resp @(http/post (str memecaptain-url "/gend_images")
                        {:headers {:Content-Type "application/json"
                                   :Accept "application/json"}
                         :body (json/encode {:src_image_id template-url
                                             :captions_attributes
                                             [{:text text-upper
                                               :top_left_x_pct 0.05
                                               :top_left_y_pct 0
                                               :width_pct 0.9
                                               :height_pct 0.25}
                                              {:text text-lower
                                               :top_left_x_pct 0.05
                                               :top_left_y_pct 0.75
                                               :width_pct 0.9
                                               :height_pct 0.25}]})})]

    (if-let [polling-url (and (= 202 (:status resp))
                              (not-empty (get-in resp [:headers "location"])))]

      (loop [attempts 0]
        (let [resp @(http/get polling-url {:follow-redirects false})
              status (:status resp)]

          (cond

            (= 303 status)
            (get-in resp [:headers "location"])

            (< 10 attempts)
            (throw (Exception. (str "Timed out waiting")))

            (= 200 status)
            (do (println "Meme not ready, sleeping")
                (Thread/sleep 1000)
                (recur (inc attempts)))

            :else
            (do (println "Something else happened")
                (throw (ex-info (str "Unexpected response from " polling-url)
                                resp))))))

      (throw (ex-info (str "Unexpected response")
                      resp)))))

(defn generate-meme [{:keys [channel text]}]
  (let [[_ template text-upper text-lower]
        (map #(.trim %) (re-matches #"\-meme <?(.*)>?\s?\|\s?(.*)\s?\|\s?(.*)\s?" text))]

    (when (not-empty template)
      (tx/say-message channel (create-instance (resolve-template-id template) text-upper text-lower)))))
