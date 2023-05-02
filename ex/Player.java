package bguspl.set.ex;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The actions queue of the player.
     */
    public BlockingQueue<Integer> actionsQueue;

    /**
     * The tokens of the player.
     */
    public List<Integer> tokens;

    /**
     * The dealer of the game.
     */
    private Dealer dealer;

    /**
     * Boolean field to know if the player received a point.
     */
    public boolean point;

    /**
     * Boolean field to know if the player received a penalty.
     */
    public boolean penalty;

    /**
     * Boolean field to know if the dealer needs to handle the actions.
     */
    public volatile boolean isAction;

    /**
     * Sleep time.
     */
    public final long almostSecond;

    /**
     * Zero time.
     */
    public final long zeroTime;

    /**
     * Time for freeze loop.
     */
    public final long freezeLoopTime;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actionsQueue = new LinkedBlockingQueue<Integer>(env.config.featureSize);
        this.tokens = new LinkedList<Integer>();
        this.point = false;
        this.penalty = false;
        this.isAction = false;
        this.almostSecond = 940;
        this.zeroTime = 0;
        this.freezeLoopTime = 980;
        }


    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            while(actionsQueue.isEmpty() && !terminate && human) {
                try {
                    synchronized(this) {
                        wait();
                    }
                } catch (InterruptedException ignored) {} 
            }
            while (!actionsQueue.isEmpty()) {
                makeAMove();
            }
            getAResult(); 

        if (!human) {
            synchronized (aiThread) {
                aiThread.notifyAll();
            }
        }
    }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
}
    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");            
            while (!terminate) {
                Random rand = new Random();
                Integer slotOfToken = rand.nextInt(env.config.tableSize);
                try {
                    synchronized (aiThread) {
                        aiThread.wait();
                        actionsQueue.offer(slotOfToken);
                    }
                } catch (InterruptedException ignored) {}    
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        synchronized(this) {
            notifyAll();
        }
        try{
            playerThread.join();
        }
        catch(InterruptedException e){}         
    
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(Integer slot) {
        if ((tokens.size() < env.config.featureSize || tokens.contains(slot)) && !isAction) {
            try{
                synchronized(actionsQueue) {
                    actionsQueue.offer(slot);
                }
                if (human) {
                    synchronized(this) {
                        notifyAll();
                    }
                }
            }
            catch(IllegalStateException ignored) {}

        } 
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        long pointFreeze = System.currentTimeMillis() + env.config.pointFreezeMillis;
        while (pointFreeze - System.currentTimeMillis() > freezeLoopTime && !terminate) {
            env.ui.setFreeze(this.id, pointFreeze - System.currentTimeMillis());
            try {
                Thread.sleep(almostSecond);
            } catch (InterruptedException ignored) {}
        }
        env.ui.setFreeze(this.id, zeroTime);
        point = false;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long penaltyFreeze = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        while (penaltyFreeze - System.currentTimeMillis() > freezeLoopTime && !terminate) {
            env.ui.setFreeze(this.id, penaltyFreeze - System.currentTimeMillis());
            try {
                Thread.sleep(almostSecond);
            } catch (InterruptedException ignored) {}
        }
        env.ui.setFreeze(this.id, zeroTime);
        penalty = false;
    }

    /**
     * Players score getter
     */
    public int score() {
        return score;
    }

    /**
     * Remove all the tokens that are currently on the table
     * @post - player has no tokens on the table.
     */
    public void removeAllTokens() {
        for (int i=0 ;i<tokens.size() ; i++) {
            table.removeToken(id, tokens.get(i));
        }
        tokens.clear();
    }

     /**
     * Getter of the players tokens
     */
    public List<Integer> getTokens() {
        List <Integer> tokensCopy = new LinkedList<>();
        for (int i=0; i<tokens.size(); i++) {
            tokensCopy.add(tokens.get(i));
        }
        return tokensCopy;
    }
    
    /**
     * Remove the token that is in this slot
     */
    public void removeToken(Integer slot) {
        tokens.remove(slot);
        table.removeToken(id, slot);
    }

    /**
     * Empty the action queue of the player
     */
    public void emptyActionQueue() {
        while (!actionsQueue.isEmpty() && !terminate) {
            actionsQueue.poll();
        }
    }

    /**
     * Player send the set to the dealer to check
     */
    public synchronized void sendSetToDealer() {
        isAction = true;
        dealer.addPlayerToQueue(id);
        dealer.getThread().interrupt();
        try {
            wait();
        } catch (InterruptedException ignored) {} 
    }           

    /**
     * the player is allowed to make a move on the table
     */
    private void makeAMove() {
        synchronized(actionsQueue) {
            Integer slot = actionsQueue.remove();
            if (tokens.contains(slot) && dealer.isTableFree && table.slotToCard[slot] != null) {
                table.removeToken(id, slot);
                tokens.remove(slot);
            }
            
            else if (tokens.size() < env.config.featureSize && table.slotToCard[slot] != null && dealer.isTableFree) {
                table.placeToken(id, slot);
                tokens.add(slot);
    
                if (tokens.size() == env.config.featureSize) {
                    sendSetToDealer();
                } 
            }  
        }   
    }

    /**
     * the player gets the result after putting a set on the table
     */
    private void getAResult() {
        if (point) {
            point();
            isAction = false;
        }
        if (penalty) {
            penalty();
            isAction = false;
        }
    }

    public void setPoint() {
        point = true;
    }


    public void setPenalty() {
        penalty = true;
    }
}
