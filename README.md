# Robbie Recursive-Descent-Parser & Interpreter:
This is a personal project I decided to take on to learn a little more about the how compilers work as well as the concepts that underly high-level languages, and to get a glimpse into the thought process that goes into creating a programming language. The project itself is an interpreter for a small scripting language I created, which is used to control a robot named 'robbie' in a 2D grid environment.

### Description:
The program is a single pass recursive-descent parser & interpreter that converts scripting language instructions into commands that control a robot in a 2D grid environment. 
'Interpreter.jar' is the program that interprets the commands. It takes a readable text file containing instructions written in "robbie instruction language", parses them using recursive descent, and interprets them as command messages which are sent across a socket connection to a host running the robbie app environment: "RobbieApp.jar".
- Additional information can be found within the javadoc located in the Documentation folder

## Requirements:
- [Java 17](https://www.oracle.com/java/technologies/downloads/#java17)
- [JavaFX](https://gluonhq.com/products/javafx/)
- [Azul Zulu](https://www.azul.com/downloads/?package=jdk#zulu)

## Scripting language specifications:
- The syntactica rules for creating a "robbie instruction language" script are listed within the EBNF document located in the Documentation folder.
- Because the EBNF document is context-free, I have also included a short informal description of the language which gives some context and descriptions to the individual terms.

## Running the program:
-  After ensuring you have met the requirements, you'll need to download the Interpreter.jar and RobbieApp.jar files
-  I have provided a TestGrid file and TestInstructions file within the documentation folder
-  RobbieApp is not currently packaged with javaFX, so you'll either have to:
   -  run from the command line and include the javaFX module path as well as the javafx.controls and javafx.fxml modules 
   -  or edit your local environment variables
- Once the RobbieApp environment is running and listening for a connection, you can then run the Interpreter.jar
#### Interpreter arguments:
-  The interpreter expects 1 to 5 arguments, specified in the RobbieInterpreter javadoc. Arguments 2 - 5 are optional with default values, and can be in any order.
1.  The first argument is required, and is expected to be the name of the script file to run (including directory path if its located in a seperate folder)
2.   "--msg": toggles the socket messaging display on for the RobbieApp. Off by default.
3.   "--port=&lt;port&gt;": sets the port number of the socket to &lt;port&gt;. 1024 by default, which is also the default for the RobbieApp.
4.   "--address=&lt;address&gt;": sets the address of the socket to &lt;address&gt;. By default this is the local machine's external IP address, which is also the default of the RobbieApp if it is ran on the same machine.
5.   "--grid=&lt;filename&gt;": initializes the grid to use during execution to &lt;filename&gt; (must include directory path if located in a different folder than RobbieApp). This is optional by default because the grid can be initialized within the robbie script. However, if a grid is not initialized before commands are ran, the program will error out.

## Future Plans:
I have plans to come back and add arguments, variables, and basic math evaluation to the scripting language in the future. I've decided to stop where I am for now since these are all fairly simple changes that, while interesting, don't really add any additional depth to the project.

#### Disclaimer:
Both the RobbieApp.jar and the MyClient class where provided to me as starting points for this project, and where not created by me.

 

