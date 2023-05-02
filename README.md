# Set_Card_Game

This implementation is a version of the game “Set”. short description of game: The game starts with 12 drawn cards from the deck that are placed on a 3x4 grid on the table. The goal of each player is to find a combination of three cards from the cards on the table that are said to make up a “legal set”. further more is explained below.

In this implementation the only threads created are: 1 thread per player + 1 additional thread for non-human players. Also, the threads do not wake up unless some actual job needs to be done (e.g., a value needs to be changed on the display, key input needs to be handled, etc.).

Additional information about implementation:

3 Version of the game "Set"

The game contains a deck of 81 cards. Each card contains a drawing with four features (color, number, shape, shading). For the sake of simplicity, in this document we describe the standard version of the game (having 4 types of features, 3 options per feature, etc.). The game starts with 12 drawn cards from the deck that are placed on a 3x4 grid on the table. The goal of each player is to find a combination of three cards from the cards on the table that are said to make up a “legal set”. A “legal set” is defined as a set of 3 cards, that for each one of the four features — color, number, shape, and shading — the three cards must display that feature as either: (a) all the same, or: (b) all different (in other words, for each feature the three cards must avoid having two cards showing one version of the feature and the remaining card showing a different version). The possible values of the features are:

▪ The color: red, green or purple.

▪ The number of shapes: 1, 2 or 3.

▪ The geometry of the shapes: squiggle, diamond or oval.

▪ The shading of the shapes: solid, partial or empty.

For example:

![image](https://user-images.githubusercontent.com/104310099/235738688-8d6d66a6-52c8-455b-8835-34a6a7a198b1.png)
![image](https://user-images.githubusercontent.com/104310099/235738799-b8da304a-0295-413b-bb69-312228c41f6d.png)
![image](https://user-images.githubusercontent.com/104310099/235738844-0c7ae6e9-0267-461b-a007-2410d82ccd5c.png)



Example1: these 3 cards do form a set, because the shadings of the three cards are all the same, while the numbers, the colors, and the shapes are all different.

![image](https://user-images.githubusercontent.com/104310099/235739267-64b831c1-87de-475a-8375-75fc8f43e3db.png)
![image](https://user-images.githubusercontent.com/104310099/235739284-c286d8ea-71c3-4615-8385-56a34c76a6ed.png)
![image](https://user-images.githubusercontent.com/104310099/235739302-c0fd79c9-30d6-4ef5-aa8b-070883a507ef.png)


Example 2: these 3 cards do not form a set (although the numbers of the three cards are all the same, and the colors, and shapes are all different, only two cards have the same shading). The game's active (i.e., initiate events) components contain the dealer and the players.

The players play together simultaneously on the table, trying to find a legal set of 3 cards. They do so by placing tokens on the cards, and once they place the third token, they should ask the dealer to check if the set is legal. If the set is not legal, the player gets a penalty, freezing his ability of removing or placing his tokens for a specified time period. If the set is a legal set, the dealer will discard the cards that form the set from the table, replace them with 3 new cards from the deck and give the successful player one point. In this case the player also gets frozen although for a shorter time period. To keep the game more interesting and dynamic, and in case no legal sets are currently available on the table, once every minute the dealer collects all the cards from the table, reshuffles the deck and draws them anew. The game will continue as long as there is a legal set to be found in the remaining cards (that are either on table or in the deck). When there is no legal set left, the game will end and the player with the most points will be declared as the winner! Each player controls 12 unique keys on the keyboard as follows. The default keys are:

![image](https://user-images.githubusercontent.com/104310099/235739351-d8b81f27-f453-49ba-8841-86b74fd488b7.png)

The keys layout is the same as the table's cards slots (3x4), and each key press dispatches the respective player’s action, which is either to place/remove a token from the card in that slot - if a card is not present/present there. The game supports 2 player types: human and non-human. The input from the human players is taken from the physical keyboard as an input. The non-human players are simulated by threads that continually produce random key presses.

4 THE GAME DESIGN

4.1 The Cards & Features

Cards are represented in the game by int values from 0 to 80. Each card has 4 features with size 3. Therefore, the features of a card can be represented as an array of integers of length 4. Each cell in the array represents a feature. The value in the cell represents one of the 3 possible values of the feature. In that way each card can get a unique id based on the features that described it and vice versa. The id of each card is calculated as follows: 30∗𝑓1+31∗𝑓2+32∗𝑓3+33∗𝑓4 where 𝑓𝑛 is the value of feature n.

4.2 The Table

The table is the data structure for the game. It is a passive object that is used by the dealer and the players to share data (more on the dealer and players later). The table holds the placed cards on a grid of 3x4, and keeps track of which token was placed by whom.

4.3 The Players

For each player, a separate thread is created to represent the player’s entity. The player object manages the player data (such as the player id and type - human/non-human). For non-human players, another thread is created to simulate key presses. You must maintain a queue of incoming actions (which are dispatched by key presses – either from the keyboard or from the simulator). The queue size must be equal to the number of cards that form a legal set (3). The player thread consumes the actions from the queue, placing or removing a token in the corresponding slot in the grid on the table. Once the player places his third token on the table, he must notify the dealer and wait until the dealer checks if it is a legal set or not. The dealer then gives him either a point or a penalty accordingly. The penalty for marking an illegal set is getting frozen for a few seconds (i.e., not being able to perform any actions). However, even when marking a legal set, the player gets frozen for a second.

4.4 The Dealer

The dealer is represented by a single thread, which is the main thread in charge of the game flow. It handles the following tasks:

▪ Creates and runs the player threads.

▪ Dealing the cards to the table.

▪ Shuffling the cards.

▪ Collecting the cards back from the table when needed.

▪ Checking if the tokens that were placed by the player form a legal set.

▪ Keeping track of the countdown timer.

▪ Awarding the player with points and/or penalizing them.

▪ Checking if any legal sets can be formed from the remaining cards in the deck.

▪ Announcing the winner(s).

Notes:

When dealing cards to the table and collecting cards from the table the dealer thread should sleep for a short period (as is already written for you in Table::placeCard and Table::removeCard methods provided in the skeleton files).
Checking user sets should be done “fairly” – if 2 players try to claim a set at roughly the same time, they should be serviced by the dealer in “first come first served” (FIFO) order. Any kind of synchronization mechanism used for this specific part of the program must take this into consideration.
4.7 PROGRAM EXECUTION / FLOW

Following is a diagram of the general flow of the program3. Following this flow should provide a fluent game experience and should also produce the correct event log output. Note that this diagram only describes the general flow. It does not address how to handle synchronization, graphic display updates and such. 
![image](https://user-images.githubusercontent.com/104310099/235739442-95ecdcdf-f614-4613-862f-baa87b05e496.png)


In this implementation the threads do not wake up unless some actual job needs to be done (e.g., a value needs to be changed on the display, key input needs to be handled, etc.) and the only threads created are: 1 thread per player + 1 additional thread for non-human players.
