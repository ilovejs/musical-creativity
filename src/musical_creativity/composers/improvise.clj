(ns musical-creativity.composers.improvise
  (:require
   [musical-creativity.util :refer :all]
   [clojure.string :as str]
   [data.forgray :refer :all]))

(def *lexicon-store* (atom {}))
(def *groupings-store* (atom {}))

(def *the-last-first-choice* (atom ()))

(def *database-names* (atom ()))

(def *lexicons* (atom ()))
(def *groupings* (atom ()))

(def seed 1)
(def *grouping-names* (atom ()))
(def *first-groupings* (atom ()))
(def tied-events ())
(def *save-groupings* ())

(def *channel-1* (atom 1))
(def *channel-2* (atom 1))
(def *channel-3* (atom 1))
(def *channel-4* (atom 1))
(def *channel-5* (atom 1))
(def *channel-6* (atom 1))
(def *channel-7* (atom 1))
(def *channel-8* (atom 1))
(def *channel-9* (atom 1))
(def *channel-10* (atom 1))
(def *channel-11* (atom 1))
(def *channel-12* (atom 1))
(def *channel-13* (atom 1))
(def *channel-14* (atom 1))
(def *channel-15* (atom 1))
(def *channel-16* (atom 1))

(defn sort-by-first-element [lists]
  (sort (fn [[x & _] [y & _]] (< x y))  lists))

(defn implode [list]
  (str/join "" list))

(defn explode [atom]
  (vec atom))

(defn get-first-pitch [events]
  "returns the first pitch in events."
  (second (first events)))

(defn within-range [number range]
  "returns t if the number is within or equal to the boundaries of the range arg."
  (if (and (>= number (first range))
           (<= number (third range))) true))

(defn all-equal? [set-1 set-2]
  (= set-1 set-2))

(defn find-in-lexicon [name]
  (@*lexicon-store* name))

(defn find-in-grouping [name]
  (@*groupings-store* name))

(defn resolve-db [name]
  (var-get (ns-resolve 'data.forgray name)))

(def new-lexicon
  {:grouping-names []
   :last-choice []})

(def new-grouping
  {:name nil
   :timing nil
   :destination []
   :events []
   :lexicon []})

(defn get-complementary-events
  "finds the complementary event to one with a tie as its final element."
  [event events]
  (cond
   (empty? events)
   nil
   (and
    (= (second event) (get-first-pitch events))
    (within-range (+ (first event) (third event))
                  (list (ffirst events) true (ffirst events)))
    (= (last-first event) 'tie))
   (cons (first events)
         (get-complementary-events (first events) (rest events)))
   :else
   (get-complementary-events event (rest events))))

(defn select [choice]
  "selects randomly from objects in the same lexicon."
  (if (zero? (get-first-pitch (:events (find-in-grouping choice))))
    choice
    (choose-one (:grouping-names (find-in-lexicon (:lexicon (find-in-grouping choice)))))))

(defn add-them [event events]
  "creates one event from two based on tie."
  (concat (take 2 event)
          (list (apply + (map third (cons event events))))
          (drop  3 event)))

(defn remove-it [remove-event events]
  "removes the first arg from the second arg once based on the first two elements."
  (cond
   (empty? events)
   ()
   (and
    (= (first remove-event) (ffirst events))
    (= (second remove-event) (get-first-pitch events)))
   (rest events)
   :else
   (cons (first events) (remove-it remove-event (rest events)))))

(defn remove-all [remove-events events]
  (if (empty? remove-events)
    events
    (remove-all (rest remove-events) (remove-it (first remove-events) events))))

(defn set-timings
  "resets the timings of the groupings so they will play consecutively."
  ([new-timings old-timings groupings] (set-timings new-timings old-timings groupings 0))
  ([new-timings old-timings groupings current-time]
     (if (or (empty? new-timings)
             (empty? groupings)
             (nil? (second (first new-timings))))
       ()
       (cons (map (fn [x](concat  (list current-time)
                                  (list (second x))
                                  (list (* (/ (third x) (- (second (first old-timings)) (first (first old-timings))))
                                           (- (second (first new-timings)) (first (first new-timings)))))
                                  (drop  3 x)))
                  (first groupings))
             (set-timings (rest new-timings)
                          (rest old-timings)
                          (rest groupings)
                          (+ current-time (- (second (first new-timings)) (ffirst new-timings))))))))

(defn find-next-new-ontime
  "finds the next new ontime past the onset events."
  ([events] (find-next-new-ontime events (ffirst events)))
  ([events time]
      (cond
       (empty? events)
       nil
       (> (ffirst events) time)
       (ffirst events)
       :else
       (find-next-new-ontime (rest events) time))))

(defn get-all-simultaneous-attacks
  "returns all of the events with the same initial ontime at the nead of events."
  ([events] (get-all-simultaneous-attacks events (ffirst events)))
  ([events time]
      (if (or (empty? events) (not (= time (ffirst events)))) ()
          (cons (first events)
                (get-all-simultaneous-attacks (rest events) time)))))

(defn clip [cutoff-time grouping]
  "clips the endings off of events which extend beyond the entrance of a new event."
  (cond
   (or (nil? cutoff-time) (empty? grouping))
   ()
   (<= (+ (ffirst grouping) (third (first grouping))) cutoff-time)
   (cons (first grouping)
         (clip cutoff-time (rest grouping)))
   :else
   (cons (concat  (take 2 (first grouping))
                  (list (- cutoff-time (ffirst grouping)))
                  (drop  3 (first grouping))
                  (list 'tie))
         (clip cutoff-time (rest grouping)))))

(defn remainder [cutoff-time grouping]
  "returns the remainder of the events which extend beyond the entrance of a new event."
  (cond
   (empty? grouping)
   ()
   (<= (+ (ffirst grouping) (third (first grouping))) cutoff-time)
   (remainder cutoff-time (rest grouping))
   :else
   (cons (concat  (list cutoff-time)
                  (list (second (first grouping)))
                  (list (- (third (first grouping)) (- cutoff-time (ffirst grouping))))
                  (drop 3 (first grouping)))
         (remainder cutoff-time (rest grouping)))))

(defn collect-groupings
  "top level function to collect groupings from the database."
  ([events] (collect-groupings events 0))
  ([events cut]
     (if (nil? (find-next-new-ontime events))
       (list (list (list cut (+ (ffirst events) (third (first events)))) events))
       (let [cutoff-time (find-next-new-ontime events)
             grouping (get-all-simultaneous-attacks events)
             clipped-grouping (clip cutoff-time grouping)]
         (cons (list (list (ffirst events) cutoff-time) clipped-grouping)
               (collect-groupings (concat (remainder cutoff-time grouping)
                                          (remove-all grouping events))
                                   cutoff-time))))))


(defn reduce-ties [events]
  "connects tied events and returns their joined composites."
  (loop [events events
         tied-events ()]
    (if (empty? events)
      tied-events
      (do
        (let [new-tied-events (when (= (last (first events)) 'tie)
                                (get-complementary-events (first events) (rest events)))]
          (if new-tied-events
            (let [events-without-tied (cons (first events) (remove-all new-tied-events (rest events)))
                  new-tied-events (butlast (add-them (first events-without-tied) new-tied-events))]
              (recur (rest events-without-tied) (concat tied-events new-tied-events)))
            (recur (rest events) (concat tied-events (first events)))))))))

(defn make-playable
  "makes the object groupings into playable events as well as recombining them with a different database timing sequence. "
  [contiguous-groupings]
  (reduce-ties
   (sort-by-first-element
         (apply concat
                (set-timings (map first (collect-groupings (resolve-db (choose-one @*database-names*))))
                             (map (fn [grouping-name] (:timing (find-in-grouping grouping-name))) contiguous-groupings)
                             (map (fn [grouping-name] (:events (find-in-grouping grouping-name))) contiguous-groupings))))))

(defn choose-beginning-grouping
  "chooses randomly from its list arg but avoids the end and rests."
  [col]
  (let [test (nth col (rand-int (count col)))]
    (cond
     (empty? (rest col)) (first col)
     (and
      (not= (:destination (find-in-grouping test)) 'end)
      (not (zero? (get-first-pitch (:events (find-in-grouping test)))))
      (not= test (:last-choice (find-in-lexicon (:lexicon (find-in-grouping test)))))
      (and (> (count col) 1) (not= @*the-last-first-choice* test)))
     test
     :else (choose-beginning-grouping col))))

(defn check-for-only-ends [groupings]
  "checks to see if the grouping contains only ending objects."
  (every? (fn [grouping]
            (= (:destination (find-in-grouping grouping)) 'end)) groupings))

(defn remove-ends
  "removes lexicons that contain only final groupings."
  [lexicons]
  (remove #(check-for-only-ends (:grouping-names (find-in-lexicon %))) lexicons))

(defn sequence-through-groupings [choice]
  "collects properly connected groupings."
  (cond
   (= choice 'end)
   ()
   (= (:destination (find-in-grouping choice)) 'end)
   (list choice)
   :else (let [new-choice (select choice)]
           (cons new-choice
                 (sequence-through-groupings (:destination (find-in-grouping new-choice)))))))

(defn choose-a-random-start-grouping
  "returns a randomly chosen object for begining a recombination."
  [lexicons]
  (let [lexicon-without-ends (remove-ends lexicons)
        lexicon-name (choose-one lexicon-without-ends)
        grouping-names (:grouping-names (find-in-lexicon lexicon-name))]
    (reset! *the-last-first-choice* (choose-beginning-grouping grouping-names)))
  @*the-last-first-choice*)

(defn interspace-hyphens [col]
  "places hyphens between the various symbols in its lits arg."
  (str/join "-" col))

(defn make-new-name-of-object [name pitches]
  "creates the names of objects that follow other objects."
  (str name "[" (inc seed) "]" "-" (interspace-hyphens pitches)))

(defn make-name-of-object [name pitches]
  "makes names for objects."
  (str name "[" (inc seed) "]" "-" (interspace-hyphens pitches)))

(defn make-name-of-lexicon [pitches]
  (str "lexicon-" (interspace-hyphens pitches)))

(defn create-database
  ([source] (create-database source true))
  ([source beginning]
     (reset! *grouping-names* ())
     (let [groupings @*groupings*]
       (loop [groupings groupings
              beginning true]
         (if (empty? groupings)
           true
           (let [name (make-name-of-object source (map second (second (first groupings))))
                 destination-name (if (nil? (second groupings))
                                        'end
                                        (make-new-name-of-object source (map second (second (second groupings)))))]
             (let [new-grouping {:name source
                                 :timing (first (first groupings))
                                 :destination destination-name
                                 :events (second (first groupings))}]

               (reset! *groupings-store* (assoc @*groupings-store* name new-grouping)))

             (reset! *grouping-names* (concat  @*grouping-names* (list name)))
             (when beginning
               (reset! *first-groupings* (concat  @*first-groupings* (list name))))
             (recur (rest groupings) false)))))
     @*grouping-names*))

(defn contains-in-lexicon? [name]
  (contains? @*lexicon-store* name))

(defn store-grouping! [lexicon-name grouping]
  (let [lexicon-record (or (@*lexicon-store* lexicon-name) new-lexicon)
        grouping-names (:grouping-names lexicon-record)
        updated-lexicon (assoc lexicon-record :grouping-names (cons grouping grouping-names))]
    (reset! *lexicon-store* (assoc @*lexicon-store* lexicon-name updated-lexicon))))

(defn store-lexicon! [grouping lexicon-name]
  (let [grouping-record (or (find-in-grouping grouping) new-grouping)
        updated-grouping (assoc grouping-record :lexicon lexicon-name)]
    (reset! *groupings-store* (assoc @*groupings-store* grouping updated-grouping))))

(defn create-database-and-put-into-lexicons [source events]
  "pujts the various data into each object and then the object itself into the proper lexicon."
   (reset! *groupings* (collect-groupings events))
   (create-database source)
   (doall (map (fn [grouping]
                 (let [lexicon-name (make-name-of-lexicon (map second (:events (find-in-grouping grouping))))]
                   (if (contains-in-lexicon? lexicon-name)
                     (do
                       (store-grouping! lexicon-name grouping)
                       (store-lexicon! grouping lexicon-name))
                     (do
                       (store-grouping! lexicon-name grouping)
                       (store-lexicon! grouping lexicon-name)
                       (reset! *lexicons* (concat  @*lexicons* (list lexicon-name)))))))
               @*grouping-names*))
   @*lexicons*)

(defn create-a-complete-database [names-of-eventlists]
  (reset! *database-names* (distinct (concat names-of-eventlists @*database-names*)))
  (doall
   (map (fn [event-list-name]
          (create-database-and-put-into-lexicons event-list-name (resolve-db event-list-name)))
        names-of-eventlists))
  true)

(defn remove-data []
  "cleans up databases for starting over."
  (reset! *first-groupings* ())
  (reset! *lexicons* ())
  (reset! *grouping-names* ())
  (reset! *groupings* ())
  (reset! *save-groupings* ())
  (reset! *database-names* ())
  (reset! test ())
  (reset! name ()))

(defn choose-grouping []
  (let [chosen-grouping (choose-a-random-start-grouping @*lexicons*)
        next-choice (:destination (find-in-grouping chosen-grouping))]
    (if (= next-choice 'end)
      (list chosen-grouping)
      (cons chosen-grouping (sequence-through-groupings next-choice)))))

(defn improvise-it
  "recombines the groupings, applies a new overall duration set, and makes the data playable."
  []
  (reduce-ties
   (make-playable
    (choose-grouping))))

(defn improvise [databases]
  (when-not (all-equal? databases @*database-names*)
    (create-a-complete-database databases))
  (improvise-it))

(defn compose []
  (improvise '(forgray)))
