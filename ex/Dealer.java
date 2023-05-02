package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    public final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Queue that contains player ids by the order of announcing sets.
     */
    public Queue<Integer> playersQueue;

    /**
     * Field for the dealer's thread.
     */
    private Thread dealerThread;

    /**
     * Keeps the table synchronized.
     */
    public volatile boolean isTableFree;

    /**
     * Sleep time.
     */
    public final long almostSecond;

    /**
     * Zero time.
     */
    public final long zeroTime;

    /**
     * Ten millis time.
     */
    public final long tenMillis;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersQueue = new LinkedList<Integer>();
        isTableFree = true;
        almostSecond = 940;
        zeroTime = 0;
        tenMillis = 10;
    };

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");        
        for (Player player: players) {
            Thread playerThread = new Thread(player);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
            for (Player player: players) {
                player.removeAllTokens();
            }
            emptyQueues(); 
        }

        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Empty action queue and tokens for each player, empty players queue for the dealer
     * @post - the queues are empty.
     */

    public void emptyQueues() {
        while (!playersQueue.isEmpty() && !terminate) {
            synchronized (players[playersQueue.peek()]) {
                players[playersQueue.remove()].notifyAll();
            }
        }

        for (Player player: players) {
            player.emptyActionQueue();
        }
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            checkSet(); 
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
            if (env.util.findSets(deck, 1).size() == 0 && !table.isSetOnTable()) {
                terminate();
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        synchronized(this) {
            notifyAll();
        }
        for (int i = players.length-1; i>=0; i--) {
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    public void removeCardsFromTable() {
        isTableFree = false;
        if (!playersQueue.isEmpty()) {
            Integer playerId = playersQueue.poll();
            if (playerId != null) {
                synchronized (players[playerId]) {
                    Player currPlayer = players[playerId];
                    List <Integer> currPlayersTokens = currPlayer.getTokens();
                    if (currPlayersTokens.size() == 3) {
                        int[] cards = new int[env.config.featureSize];
                        for (int i = 0; currPlayersTokens!=null && i<currPlayersTokens.size(); i++) {
                            int card = table.slotToCard[currPlayersTokens.get(i)];
                            cards[i] = card;
                        }
                        if (env.util.testSet(cards)) {
                            for (int i = 0; i < currPlayersTokens.size(); i++) {
                                table.removeCard(currPlayersTokens.get(i));
                            }
                            isTableFree = true;
                            for (Player player: players) {
                                List<Integer> otherPlayerTokens = player.getTokens();
                                if (player.id != playerId) {
                                    for (int i=0; i<currPlayersTokens.size(); i++) {
                                        int currSlot = currPlayersTokens.get(i);
                                        if (currPlayersTokens != null && otherPlayerTokens.contains(currSlot)) { 
                                            player.removeToken(currSlot);
                                        }
                                    }
                                } 
                            }
                            currPlayer.removeAllTokens();
                        } 
                    }       
                }
            }
        }       
    }

    /**
     * Check if any cards can be placed on the table , and place them.
     */
    private void placeCardsOnTable() {
        isTableFree = false;
        for (int i=0; i<table.slotToCard.length; i++) {
            if (table.slotToCard[i]==null) {
                if (!deck.isEmpty()) {
                    Random rand = new Random();
                    int index = rand.nextInt(deck.size());
                    int card = deck.get(index);
                    deck.remove(index);
                    table.placeCard(card,i);
                }
            }
        }
        isTableFree = true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized(this) {
                if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) {
                    Thread.sleep(almostSecond);
                }
                else {
                    Thread.sleep(tenMillis);
                }
            }
        } 
        catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        long gap = reshuffleTime-System.currentTimeMillis();
        boolean warn = false;
        if (gap < env.config.turnTimeoutWarningMillis) {
            warn = true;
        }       
        if (gap > zeroTime) {
            env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), warn);
        } 
    }

    /**
     * Returns all the cards from the table to the deck.
     * @post - table has no cards (slots are empty).
     */
    public void removeAllCardsFromTable() {
        isTableFree = false;
        for (Player player: players) {
            player.removeAllTokens();
        }
        for (int i=0; i<table.slotToCard.length; i++) {
            Integer currSlot = table.slotToCard[i];
            if (currSlot != null) {
                deck.add(currSlot);
                table.removeCard(i);
            }
        }
        isTableFree = true;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxTemp = players[0].score();
        int[] winner = new int[1];
        winner[0] = players[0].id;

        for (int i=1; i<players.length; i++) {
            if (maxTemp < players[i].score()) {
                maxTemp = players[i].score();
                winner[0] = players[i].id;
            }
        }

        int counter = 0;
        for (int i=0; i<players.length; i++) {
            if (maxTemp == players[i].score()) {
                counter++;
            }
        }
        if (counter > 1) {
            int[] winners = new int[counter];
            for (int i=0; i<players.length; i++) {
                if (maxTemp == players[i].score()) {
                    winners[counter-1] = players[i].id;
                    counter--;
                }
            }
            env.ui.announceWinner(winners);
        }
        else if (counter == 1) {
            env.ui.announceWinner(winner);
        }
    }
    
    /**
     * add the player to the player queue
     * @post - size of the queue increased by 1
     */
    public void addPlayerToQueue(int playerId) {
        playersQueue.add(playerId);
    }
    
    /**
     * getter thread
     */
    public Thread getThread() {
        return dealerThread;
    }

    /**
     * Method for the dealer to check a set that was sent from a player
     */
    public void checkSet() {
        isTableFree = false;
        if (!playersQueue.isEmpty()) { 
            Integer playerId = playersQueue.peek();
            if (playerId != null) {
                synchronized (players[playerId]) {
                    List <Integer> currSet = players[playerId].getTokens();
                    if (currSet.size() == env.config.featureSize) {
                        int[] cards = new int[env.config.featureSize];
                        for (int i = 0; i < currSet.size(); i++) {
                            int card = table.slotToCard[currSet.get(i)];
                            cards[i] = card;
                        }
                        if (env.util.testSet(cards)) {
                            players[playerId].setPoint();
                            updateTimerDisplay(true);
                        }
                        else {
                            players[playerId].setPenalty();
                        }
                    } 
                players[playerId].notifyAll();   
                isTableFree = true;  
                }
            }
        }  
    }
           
           
}
