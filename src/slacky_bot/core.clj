(ns slacky-bot.core
  (:gen-class)
  (:require
   [clojure.repl :refer [pst]]
   [clj-slack-client
    [core :as slack]
    [team-state :as state]
    [web :as web]
    [rtm-transmit :as tx]]
   [slacky-bot
    [meme :as meme]]))

(def ^:dynamic *api-token* nil)

(defn- find-command [text]
  (condp re-matches text

    #"^\-slacky.*" (fn [{:keys [channel]}] (tx/say-message channel "Hello, I'm here"))

    #"^\-meme .*" meme/generate-meme

    nil))


(defn handle-message
  "translates a slack message into a command, handles that command, and communicates the reply"
  [{channel-id :channel, text :text, :as msg}]
  (try
    (when-let [command (find-command text)]
      (command msg))
    (catch Exception ex
      (pst (Exception. (str "Exception trying to handle slack message\n" (str msg) ".") ex))
      (try (tx/say-message channel-id "You broke me. Check my logs for the exception")))))


(defn dispatch-handle-slack-event [event] ((juxt :type :subtype) event))

(defmulti handle-slack-event #'dispatch-handle-slack-event)

(defmethod handle-slack-event ["message" nil]
  [{user-id :user, :as msg}]
  (when (not (state/bot? user-id))
    (handle-message msg)))

(defmethod handle-slack-event ["message" "message_changed"]
  [event]
  nil)

(defmethod handle-slack-event ["channel_joined" nil]
  [event]
  nil)

(defmethod handle-slack-event :default
  [event]
  nil)


(defn try-handle-slack-event
  [event]
  (try
    (handle-slack-event event)
    (catch Exception ex
      (pst (Exception. (str "Exception trying to handle slack event\n" (str event) ".")) ex))))


(defn wait-for-console-quit []
  (loop []
    (let [input (read-line)]
      (when-not (= input "q")
        (recur)))))


(defn shutdown-app []
  (slack/disconnect)
  (println "...slacky-bot dying"))


(defn stop []
  (shutdown-app))

(defn start
  ([]
   (start (.trim (slurp "api-token.txt"))))
  ([api-token]
   (try
     (alter-var-root (var *api-token*) (constantly api-token))
     (slack/connect *api-token* try-handle-slack-event)
     (println "slacky-bot is running")
     (catch Exception ex
       (println ex)
       (println "couldn't start slacky-bot")
       (stop)))))

(defn restart []
  (stop)
  (start))

(defn -main
  [& args]
  (try
    (start)
    (wait-for-console-quit)
    (finally
      (stop)
      (shutdown-agents))))
