# Tic Tac Toe

In this example, we will briefly go over how to build a tic tac toe game
which is backed by the Radix Engine.

## Main Files

* [TicTacToeBaseParticle.java](TicTacToeBaseParticle.java) - Base particle which contains data
for the game state
* [TicTacToeConstraintScrypt.java](TicTacToeConstraintScrypt.java) - Constraint Scrypt which
defines the Tic Tac Toe extended state machine
* [TicTacToeMoveTransitionProcedure.java](TicTacToeMoveTransitionProcedure.java) - A transition
procedure which defines constraints on transitions
* [TicTacToeRunner.java](TicTacToeRunner.java) - The executable which contains `main()` and puts
it all together

## Finite State Machines

The Radix Engine works with well defined states and transitions on top of which a Finite State Machine
(FSM) is incredibly suited.

There are two main objects in an FSM: States and State Transitions. In the Radix Engine, these are
represented by Particles and TransitionTokens respectively.

|FSM Object|Represented in Radix Engine as a|
|-----|------------------------------|
|State|Particle Class|
|Transition|Transition Token|

## Tic Tac Toe Finite State Machine

Luckily for us, Tic Tac Toe is a bounded game with a finite number of possible states and transitions.
This makes the game a prime target for Radix Engine implementation.

Unlucky for us, even though it is bounded, mapping out each of the game states and every possible
transition would quickly explode the size of a Tic Tac Toe FSM:
 
<img src="https://yuml.me/diagram/scruffy/class/[___;___;___]->[___;_X_;___],[___;___;___]->[___;___;__X], [___;___;___]->[X__;___;___],[___;___;___]->[_X_;___;___],[___;___;___]->[__X;___;___],[___;___;___]->[___;X__;___], [___;___;___]->[___;__X;___],[___;___;___]->[___;___;X__], [___;___;___]->[___;___;__X],[___;___;__X]->[O__;___;__X],[___;___;__X]->[_O_;___;__X],[___;___;__X]->[__O;___;__X],[___;___;__X]->[___;O__;__X],[___;___;__X]->[___;_O_;__X],[___;___;__X]->[___;__O;__X],[___;___;__X]->[___;___;O_X],[___;___;__X]->[___;___;_OX]," width=400 />

To mitigate this we use the help of an extended state machine.

## Extended State Machines

Extended State Machines differ from Finite State Machines in that they are composed of two types of
state rather than just one. The original State still exists but now additionally we add an extended
state which are variables or data stored under a State. Furthermore, transitions can now also be
dependent on this extended state, these are called guards.

This leads us to introducing a few other representations:

|Extended State Machine Object|Represented in Radix Engine as a|
|-----|------------------------------|
|State|Particle Class|
|Extended State|Particle Data|
|Transition|Transition Token|
|Transition Guards|Transition Procedure|

## Tic Tac Toe Extended State Machine

If we were to design the state machine of Tic Tac Toe now we can vastly simplify it by using State
to represent clear qualitative aspects of the game (e.g. who's turn it is) and encode the quantitative
aspects (e.g. the board itself) as data in each of those States:

<img src="https://yuml.me/diagram/scruffy/class/%5Bstart%5D-is%20empty%20board%3F%3E%5BX%20to%20Move%5D%2C%5BX%20to%20Move%7CX%20to%20move%20board%5D-valid%20move%3F%3E%5BO%20to%20Move%7CO%20to%20move%20board%5D%2C%5BO%20to%20Move%5D-valid%20move%3F%3E%5BX%20to%20Move%5D%2C%5BO%20to%20Move%5D-valid%20move%3F%3E%5BO%20wins%7C%20O%20winning%20board%5D%2C%20%5BX%20to%20Move%5D-valid%20move%3F%3E%5BX%20wins%7CX%20winning%20board%5D%2C%5BX%20to%20Move%5D-valid%20move%3F%3E%5BDraw%7CDraw%20Board%5D" width=400 />

Here we end up with 5 states (not including start) and 6 transitions, much more manageable!

We will now show how this is coded into the Radix Engine.

## Tic Tac Toe Constraint Scrypt

Before we can program the engine we need an entrypoint for our new code. This is done by creating a
`ConstraintScrypt` which has a `main()` method entrypoint similar to regular applications. 

```java
public class TicTacToeConstraintScrypt implements ConstraintScrypt {
  @Override
  public void main(SysCalls os) {
  }
}
```

The `Syscalls` object passed into `main()` contains the low level api which the constraint scrypt can
use to program the engine.

## Tic Tac Toe Particles and Particle Data

To start off with, we create five particle classes which correspond to the the five states in our
extended state machine and define the data each of these states will have. Because the data is exactly
the same in all five states (we just need a board and the two player playing) we'll create a
`TicTacToeBaseParticle.java` which the five states can simply extend:

```java
abstract class TicTacToeBaseParticle extends Particle {
  enum TicTacToeSquare { X, O, EMPTY }
  public static final int TIC_TAC_TOE_BOARD_SIZE = 9;

  @JsonProperty("xPlayer")
  @DsonOutput(Output.ALL)
  private final RadixAddress xPlayer;

  @JsonProperty("oPlayer")
  @DsonOutput(Output.ALL)
  private final RadixAddress oPlayer;

  @JsonProperty("board")
  @DsonOutput(Output.ALL)
  private final ImmutableList<TicTacToeSquare> board;

  TicTacToeBaseParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
    super(ImmutableSet.of(xPlayer.getUID(), oPlayer.getUID()));
  
    this.xPlayer = xPlayer;
    this.oPlayer = oPlayer;
    this.board = board;
  }

  public final ImmutableList<TicTacToeSquare> getBoard() {
    return board;
  }

  public final RadixAddress getXPlayer() {
    return xPlayer;
  }

  public final RadixAddress getOPlayer() {
    return oPlayer;
  }
}
```

```java
@SerializerId2("o-to-move-particle")
class OToMoveParticle extends TicTacToeBaseParticle {
  OToMoveParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
    super(xPlayer, oPlayer, board);
  }
}
```

```java
@SerializerId2("x-to-move-particle")
class XToMoveParticle extends TicTacToeBaseParticle {
  XToMoveParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
    super(xPlayer, oPlayer, board);
  }
}
```

```java
@SerializerId2("o-wins-particle")
class OWinsParticle extends TicTacToeBaseParticle {
  OWinsParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
    super(xPlayer, oPlayer, board);
  }
}
```

```java
@SerializerId2("x-wins-particle")
class XWinsParticle extends TicTacToeBaseParticle {
  XWinsParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
    super(xPlayer, oPlayer, board);
  }
}
```

```java
@SerializerId2("draw-particle")
class DrawParticle extends TicTacToeBaseParticle {
  DrawParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
    super(xPlayer, oPlayer, board);
  }
}
```
The above `@JsonProperty` and `@DsonOutput` java annotations are currently required for serialization
and content based hashing purposes.

In our Constraint Scrypt we now need to register these particles:

```java
public class TicTacToeConstraintScrypt implements ConstraintScrypt {
  private static Result staticCheck(TicTacToeBaseParticle ticTacToe, GameStatus requiredGameStatus) {
    if (ticTacToe.getBoard() == null) {
      return Result.error("Tic Tac Toe board cannot be null.");
    }

    if (ticTacToe.getBoard().size() != TicTacToeBaseParticle.TIC_TAC_TOE_BOARD_SIZE) {
      return Result.error("Tic Tac Toe board must be size 9.");
    }

    for (TicTacToeSquare square : ticTacToe.getBoard()) {
      if (square == null) {
        return Result.error("No square can be null.");
      }
    }

    if (ticTacToe.getXPlayer() == null) {
      return Result.error("X player cannot be null.");
    }

    if (ticTacToe.getOPlayer() == null) {
      return Result.error("O player cannot be null.");
    }

    GameStatus gameStatus = null;
    for (ImmutableList<Integer> line : LINES) {
      ImmutableList<TicTacToeSquare> board = ticTacToe.getBoard();
      if (board.get(line.get(0)) != TicTacToeSquare.EMPTY
        && board.get(line.get(0)) == board.get(line.get(1))
        && board.get(line.get(1)) == board.get(line.get(2))) {
        gameStatus = board.get(line.get(0)) == TicTacToeSquare.X ? GameStatus.X_WINS : GameStatus.O_WINS;
        break;
      }
    }

    if (gameStatus == null) {
      gameStatus = ticTacToe.getBoard().stream().allMatch(s -> s != TicTacToeSquare.EMPTY) ? GameStatus.DRAW : GameStatus.IN_PROGRESS;
    }

    if (gameStatus != requiredGameStatus) {
      return Result.error("Required game state is " + requiredGameStatus + " but was " + gameStatus);
    }

    return Result.success();
  }
	
  @Override
  public void main(SysCalls os) {
    os.registerParticleMultipleAddresses(
      XToMoveParticle.class,
      ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
      t -> staticCheck(t, GameStatus.IN_PROGRESS)
    );
    os.registerParticleMultipleAddresses(
      OToMoveParticle.class,
      ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
      t -> staticCheck(t, GameStatus.IN_PROGRESS)
    );
    os.registerParticleMultipleAddresses(
      XWinsParticle.class,
      ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
      t -> staticCheck(t, GameStatus.X_WINS)
    );
    os.registerParticleMultipleAddresses(
      OWinsParticle.class,
      ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
      t -> staticCheck(t, GameStatus.O_WINS)
    );
    os.registerParticleMultipleAddresses(
      DrawParticle.class,
      ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
      t -> staticCheck(t, GameStatus.DRAW)
    );
  }
}
```

We register each particle with multiple addresses so the game gets stored in the address of both
players.

The `staticCheck` is used to verify that the board and players in each State is well formed given
their State definition.

## Tic Tac Toe Transition Tokens

We now need to code in the transitions of our state machine. For the engine, this is done via
Transition Tokens. Based on our earlier state machine we've got six transitions to code up:

```java
public class TicTacToeConstraintScrypt implements ConstraintScrypt {
	
// ...

  @Override
  public void main(SysCalls os) {
  
// ...

    TransitionToken<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData> newGameToken = new TransitionToken<>(
      VoidParticle.class, TypeToken.of(VoidUsedData.class), XToMoveParticle.class, TypeToken.of(VoidUsedData.class)
    );
    TransitionToken<XToMoveParticle, VoidUsedData, OToMoveParticle, VoidUsedData> xMovesToken = new TransitionToken<>(
      XToMoveParticle.class, TypeToken.of(VoidUsedData.class), OToMoveParticle.class, TypeToken.of(VoidUsedData.class)
    );
    TransitionToken<OToMoveParticle, VoidUsedData, XToMoveParticle, VoidUsedData> oMovesToken = new TransitionToken<>(
      OToMoveParticle.class, TypeToken.of(VoidUsedData.class), XToMoveParticle.class, TypeToken.of(VoidUsedData.class)
    );
    TransitionToken<OToMoveParticle, VoidUsedData, OWinsParticle, VoidUsedData> oWinsToken = new TransitionToken<>(
      OToMoveParticle.class, TypeToken.of(VoidUsedData.class), OWinsParticle.class, TypeToken.of(VoidUsedData.class)
    );
    TransitionToken<XToMoveParticle, VoidUsedData, XWinsParticle, VoidUsedData> xWinsToken = new TransitionToken<>(
      XToMoveParticle.class, TypeToken.of(VoidUsedData.class), XWinsParticle.class, TypeToken.of(VoidUsedData.class)
    );
    TransitionToken<XToMoveParticle, VoidUsedData, DrawParticle, VoidUsedData> drawsToken = new TransitionToken<>(
      XToMoveParticle.class, TypeToken.of(VoidUsedData.class), DrawParticle.class, TypeToken.of(VoidUsedData.class)
    );
  }
}
```

Above, we defined each Transition Token by specifying the input Particle class and the output
Particle class.

The `VoidParticle` used in the first `newGameToken` signifies that there is no input Particle class
and that it is the start of the state machine.

The `VoidUsedData` used in all of the Transition Tokens can simply be thought of as null, the use
of that field is for more advanced use and will not be covered in this example.

## Tic Tac Toe Transition Procedures

Now we need to register the transition tokens along with the transition procedures associated with
them.

Transition procedures contain the logic asserting that given the transition token and the Particle
Data associated with the input and outputs of the Transition token is valid.

```java
public class TicTacToeConstraintScrypt implements ConstraintScrypt {

// ...

  @Override
  public void main(SysCalls os) {
  
// ...

    os.createTransition(newGameToken, new TransitionProcedure<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData>() {
      @Override
      public Result precondition(VoidParticle inputParticle, VoidUsedData inputUsed, XToMoveParticle outputParticle, VoidUsedData outputUsed) {
        for (int squareIndex = 0; squareIndex < 9; squareIndex++) {
          TicTacToeSquare nextSquareState = outputParticle.getBoard().get(squareIndex);

          if (nextSquareState != TicTacToeSquare.EMPTY) {
            return Result.error("Game must start with an empty board");
          }
        }

        return Result.success();
      }

      @Override
      public UsedCompute<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData> inputUsedCompute() {
        return (in, inUsed, out, outUsed) -> Optional.empty();
      }

      @Override
      public UsedCompute<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData> outputUsedCompute() {
        return (in, inUsed, out, outUsed) -> Optional.empty();
      }

      @Override
      public WitnessValidator<VoidParticle> inputWitnessValidator() {
        return (p, w) -> WitnessValidatorResult.success();
      }

      @Override
      public WitnessValidator<XToMoveParticle> outputWitnessValidator() {
        return (p, w) -> w.isSignedBy(p.getXPlayer().getKey()) || w.isSignedBy(p.getOPlayer().getKey())
          ? WitnessValidatorResult.success()
          : WitnessValidatorResult.error("Game must be started by either one of the players.");
      }
    });

    os.createTransition(xMovesToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.X));
    os.createTransition(oMovesToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.O));
    os.createTransition(xWinsToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.X));
    os.createTransition(oWinsToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.O));
    os.createTransition(drawsToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.X));
  }
}
```

The above TransitionProcedure for the `newGameToken` transition token has five implemented methods.
We will only be concerned with the `precondition()` and `outputWitnessVaildator()` methods in this
example.

The `precondition()` method takes in the input particle and output particle and based on this we
define when this transition is valid. For the `newGameToken` transition we only need to check that
the board which we are creating is fully empty. We don't want to allow someone to create a new
tic tac toe game with X's and O's already in play.

The `outputWitnessValidator` method specifies the permissions for actually performing this
transition. In this case we check that a game can only be started if either one of the players
involved in the game cryptographically signed the atom.

We leave the rest of the transition procedures as an exercise.

## Running on the Radix Engine

Now that our ConstraintScrypt is complete we need to load in up into engine.

We do this by loading it up into a `CMAtomOS` which manages how Constraint Scrypts get loaded
into an engine:

```java
public class TicTacToeRunner {
  public static void main(String[] args) throws CryptoException {
    CMAtomOS cmAtomOS = new CMAtomOS();
    cmAtomOS.load(new TicTacToeConstraintScrypt());
    ConstraintMachine cm = new ConstraintMachine.Builder()
      .setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
      .setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
      .build();
    InMemoryEngineStore<BasicRadixEngineAtom> engineStore = new InMemoryEngineStore<>();
    RadixEngine<BasicRadixEngineAtom> engine = new RadixEngine<>(
      cm,
      cmAtomOS.buildVirtualLayer(),
      engineStore
    );
    engine.start();
  }
}
```

Congratulations! You have the tic tac toe game running on the Radix Engine!

## Executing a Tic Tac Toe Move

Now how do you actually use this engine that's been created? Atoms must be created with correct
particles and spins. The mechanism to do this is quite low level and complex at the moment so
we will just include the relevant code below found in `TicTacToeRunner.java`.

```java
public class TicTacToeRunner {

// ...

  private static BasicRadixEngineAtom buildAtom(
    String description,
    TicTacToeBaseParticle prevBoard,
    TicTacToeBaseParticle nextBoard,
    ECKeyPair player
  ) throws CryptoException {

    // Program the board transition
    ImmutableList.Builder<CMMicroInstruction> microInstructions = ImmutableList.builder();
    if (prevBoard != null) {
      microInstructions.add(CMMicroInstruction.checkSpin(prevBoard, Spin.UP));
      microInstructions.add(CMMicroInstruction.push(prevBoard));
    }
    microInstructions.add(CMMicroInstruction.checkSpin(nextBoard, Spin.NEUTRAL));
    microInstructions.add(CMMicroInstruction.push(nextBoard));
    microInstructions.add(CMMicroInstruction.particleGroup());

    CMInstruction instruction = new CMInstruction(
      microInstructions.build(),
      Hash.ZERO_HASH,
      ImmutableMap.of(player.getUID(), player.sign(Hash.ZERO_HASH))
    );

    return new BasicRadixEngineAtom(instruction, description);
  }
	
  public static void main(String[] args) throws CryptoException {
  
// ...

    BasicRadixEngineAtom atom = buildAtom("Legal Initial board", null, initialBoard, xPlayer);
    
    engine.store(atom, new AtomEventListener<BasicRadixEngineAtom>() {
      @Override
      public void onCMError(BasicRadixEngineAtom cmAtom, CMError error) {
        System.out.println("ERROR:   " + cmAtom + " CM verification " + error);
      }

      @Override
      public void onStateConflict(BasicRadixEngineAtom cmAtom, DataPointer issueParticle, BasicRadixEngineAtom conflictingAtom) {
        System.out.println("ERROR:   " + cmAtom + " Conflict with atom " + conflictingAtom);
      }

      @Override
      public void onStateStore(BasicRadixEngineAtom cmAtom) {
        System.out.println("SUCCESS: " + cmAtom);
      }
    });
  }
}
```
