package main;

import java.io.RandomAccessFile;
import java.util.HashMap;

/**
 * This class is a recursive-descent parser which provides functionality for parsing scripts
 * written in robbie instruction language and interpreting them from instructions into
 * CommandInterface commands to control robbie and the RobbieApp environment.
 *<p>
 * The structural rules of the robbie instruction language are documented in the EBNF document.
 * @author Jared Schimpf
 */
public class Parser {

    RandomAccessFile mFile;
    
    Tokenizer tokenizer;

    CommandInterface mCommandInterface;

    /**
     * Constructor
     * 
     * @param file The file to be read, which contains the robbie instruction script
     * @param commandInterface Interface which handles the socket connection, used to send commands to the RobbieApp
     * @throws Exception
     */
    public Parser(RandomAccessFile file, CommandInterface commandInterface) throws Exception {
        mFile = file;
        tokenizer = new Tokenizer(file);
        mCommandInterface = commandInterface;
    }


    //maps a procedure name to its definition location in the file.
    public HashMap <String, Long> procMap = new HashMap<>();

    /**
     * Initiates the parsing and interpretation process
     * @throws Exception if parsing or execution fails
     */
    public void start() throws Exception {
        parseProgram();
    }

    /**
     * Outermost layer of recursive descent.
     * A program is defined as any number of procedures followed by a single main.
     * <p>
     * This method first checks for any procedures and calls their parsing method,
     * then calls for the parsing method for main
     * @throws Exception if parsing or execution fails
     */
    private void parseProgram() throws Exception {
        while (tokenizer.peek().equals("proc")){
            parseProc();
        }
        parseMain();
        mCommandInterface.stop();
    }

    /**
     * Main is defined by "main" followed by 1 or more instructions enclosed in curly braces
     * <p>For the sake of code reusability, the logic for interpreting multiple instructions inside a curly brace
     * has been seperated into its own method since it is used in other parsing besides main.
     * @throws Exception if parsing or execution fails
     */
    private void parseMain() throws Exception {
        tokenizer.eat("main");
//        while(!tokenizer.peek().equals("}")){
//            parseInstr();
//        }
        parseMultilineInstr();
    }

    /**
     * A procedure is defined by "proc" followed by a name, followed by 1 or more instructions enclosed in curly braces.
     * <p>
     * this method stores the location and name of the procedure in the procMap,
     * which will be referred back to whenever the stored procedure is called.
     * @throws Exception if parsing or execution fails
     */
    private void parseProc() throws Exception{
        tokenizer.eat("proc");
        String name = parseName();
        long offset = mFile.getFilePointer();
        procMap.put(name, offset);
        parseMultilineInstr(false);

    }

    /**
     * An instruction is defined as the following:
     * <p> - Any number of commands
     * <p> - A conditional: if, while, do
     * <p> - A procedure call
     * <p> - An init call
     * <p> - A print statement
     *
     * @throws Exception If an unrecognized instruction is given, or if parsing or execution fails
     */
    private void parseInstr() throws Exception{
        String scry = tokenizer.peek();

         switch (scry){
            case "step", "turnL", "turnR", "take", "drop":
                parseCommands();
                break;
            case "if":
                parseIf();
                break;
            case "while":
                parseWhile();
                break;
            case "do":
                parseDo();
                break;
            case "call":
                parseCall();
                break;
            case "init":
                parseInit();
                break;
            case "print":
                parsePrint();
                break;
            default:
                throw new Exception(String.format("Invalid Instruction: \"%s\" is not a recognized instruction", scry));
        }
    }

    /**
     * A print statement is defined as "print" followed by a string, followed by a ";"
     * The print instruction will print messages to the local machine, not the host machine.
     * @throws Exception if parsing or execution fails
     */
    private void parsePrint() throws Exception {
        tokenizer.eat("print");
        System.out.println(parseStr());
        tokenizer.eat(";");

    }

    /**
     * A string is defined as any number of characters enclosed by quotation marks
     * @return the parsed string
     * @throws Exception if the parsed token is not a valid string, or if parsing or execution fails
     */
    private String parseStr() throws Exception {
        String token = tokenizer.next();
        if(token.charAt(0)=='"' &&token.charAt(token.length()-1)=='"'){
            return token.substring(1,token.length()-1);
        }
        throw new Exception(String.format("Unrecognized Token: expected a string but found \"%s\" instead. Strings must be surrounded by \'\"\'", token));
    }

    /**
     * An initialization call is defined by "init" followed by either
     * "grid" and a string representing the grid file name
     * or "gems" and an integer representing an amount,
     * then it is ended by a ";"
     * @throws Exception if the parsed token after "init" is unrecognized, or if parsing or execution fails
     */
    private void parseInit() throws Exception{
        tokenizer.eat("init");
        String token = tokenizer.next();
        if(token.equals("grid")) {
            String filename = tokenizer.next();
            mCommandInterface.initGrid(filename);
        }
        else if(token.equals("gems")){
            int gems = parseInteger();
            mCommandInterface.initGems(gems);
        }
        else throw new Exception(String.format("Unrecognized Argument: \"%s\" is not a valid argument for init", token));
        tokenizer.eat(";");
    }

    /**
     * A procedure call is defined by "call" followed by a name representing the procedure the call, then a ";"
     * <p>
     * The calling of a stored procedure is handled by saving the current file pointer location, then moving the pointer backwards
     * to where the procedure's body is defined and executing the instructions in its body.
     * The parser then jumps back to the saved pointer location after the procedure call.
     * @throws Exception if parsing or execution fails
     */
    private void parseCall() throws Exception{
        tokenizer.eat("call");
        String name = parseName();
        long ptr = mFile.getFilePointer();
        mFile.seek(procMap.get(name));
        parseMultilineInstr();
        mFile.seek(ptr);
        tokenizer.eat(";");
    }

    /**
     * A do loop is a type of conditional with two definitions, a single instruction and multi-line definition:
     * <p> - Single instruction: "do" followed by an integer and an instruction
     * <p> - Multiline": "do" followed by an integer, and then 1 or more instructions enclosed by curly braces
     * <p> A do loop will execute the instructions in its body a number of times specified by the integer given after "do"
     * @throws Exception if parsing or execution fails
     */
    private void parseDo() throws Exception{
        tokenizer.eat("do");
        int i = parseInteger();
        long ptr = mFile.getFilePointer();
        for(; i > 0; i--){
            mFile.seek(ptr);
            conditionalParse(true);
        }
    }

    /**
     * An integer is defined as 1 or more digits.
     * This method attempts to parse a token as an integer, throwing an error if it fails.
     * @return integer value of the token
     * @throws Exception if the token cannot be parsed as an int
     */
    private int parseInteger() throws Exception {
        String token = tokenizer.next();
        try {
            return Integer.parseInt(token);
        }
        catch (Exception e){
            throw new Exception(String.format("Unrecognized Token: Expected an integer but found \"%s\" instead", token));
        }

    }


    //ok, two ways to do the while loop:
    //  1. change parseTest to accept the token it evaluates as an argument, so that we can save the token
    //     and constantly recheck its condition in a while loop that runs the instructions in its body, or...
    //  2. this:

    /**
     * A while loop is a type of conditional with two definitions,a single instruction and multi-line definition:
     * <p> - Single instruction: "while" followed by a test and an instruction
     * <p> - Multiline": "do" followed by a test, and then 1 or more instructions enclosed by curly braces
     * <p> A while loop will execute the instructions in its body in a loop until the test that is specified after the "while" returns false.
     * <p>
     * The parsing of a while loop has been split into two separate methods as a consequence of
     * handling the problem with recursion. This method simply consumes the "while" token and initiates
     * the looping conditional parse of the while statement's body
     * @throws Exception if parsing or execution fails
     */
    private void parseWhile() throws Exception{
        tokenizer.eat("while");
        whileLoop();
    }

    /**
     * Internal logic for the while loop. This method stores the pointer at the start of the loop declaration
     * before the test is parsed, then executes the body of the loop dependent on the result of the test.
     * If the test returns true, this method then returns to the saved pointer location and calls itself again.
     * This forms a recursive execution loop for as long as the test condition remains true.
     * @throws Exception if parsing or execution fails
     */
    private void whileLoop() throws Exception{
        long ptr = mFile.getFilePointer();
        boolean cond = parseTest();
        conditionalParse(cond);

        if(cond){
            mFile.seek(ptr);
            whileLoop();
        }

        return;
    }

    /**
     * An if statement is a type of conditional with two definitions,a single instruction and multi-line definition:
     * <p> - Single instruction: "if" followed by a test and an instruction
     * <p> - Multiline": "if" followed by a test, and then 1 or more instructions enclosed by curly braces
     * <p> An if statement will execute the instructions in its body only if its test condition returns true.
     * <p>
     * An if statement may be followed by an optional else statement, which follows similar syntactical rules:
     * <p> - Single instruction: "else" followed by an instruction.
     * <p> - Multiline": "else" followed by 1 or more instructions enclosed by curly braces
     * @throws Exception if parsing or execution fails
     */
    private void parseIf() throws Exception{
        tokenizer.eat("if");
        boolean cond = parseTest();
        conditionalParse(cond);

        if(tokenizer.peek().equals("else")){
            tokenizer.eat("else");
            conditionalParse(!cond);
        }
    }

    /**
     * This method handles parsing or skipping an instruction depending on a passed in boolean condition
     * @param cond the condition to check before executing
     * @throws Exception if parsing or execution fails
     */
    private void parseInstr(boolean cond) throws Exception {
        if(cond){
            parseInstr();
            return;
        }
        else while(!tokenizer.peek().equals(";")){
            tokenizer.next();
        }
        tokenizer.eat(";");
    }

    /**
     * This method handles parsing or skipping a multi-line (curly brace) instruction depending on a passed in boolean condition
     * @param cond the condition to check before executing
     * @throws Exception if parsing or execution fails
     */
    private void parseMultilineInstr(boolean cond) throws Exception {
        tokenizer.eat("{");
        int openCount = 1;
        int closeCount = 0;

        if(cond){
            while(!tokenizer.peek().equals("}")){
                parseInstr();
            }
        }
        else while(openCount > closeCount){
            tokenizer.next();
            if(tokenizer.peek().equals("{")){
                openCount++;
            }
            if(tokenizer.peek().equals("}")){
                closeCount++;
            }
        }
        tokenizer.eat("}");
    }

    /**
     * This method handles parsing a multi-line instruction, which is one or more instructions
     * that exist inside the execution body of a term (such as a conditional or a procedure, or main itself) and are enclosed by curly braces.
     * @throws Exception if parsing or execution fails
     */
    private void parseMultilineInstr() throws Exception {
        tokenizer.eat("{");
        while(!tokenizer.peek().equals("}")){
            parseInstr();
        }
        tokenizer.eat("}");
    }

    /**
     * This method checks whether the following token is the beginning of a multi-line instruction
     * or a single instruction, and calls the relevant condition based parsing method.
     * This method exists to isolate some repeated logic that was used in multiple parsing methods
     * @param cond the condition to check before executing
     * @throws Exception
     */
    private void conditionalParse(boolean cond) throws Exception {
        if(tokenizer.peek().equals("{")){
            parseMultilineInstr(cond);
        }
        else parseInstr(cond);
    }

    /**
     * A test is a command that returns a boolean value based on a condition in the RobbieApp environment
     * <p>A test is syntactically defined as an optional "!" or "not" symbolizing a negation, followed by
     * one of the following test conditions:
     * <p>"leftclear" | "rightclear" | "frontclear" | "backclear" | "seejem" | "hasjem" | "facingN" | "facingW" | "facingS" | "facingE"
     * @return boolean result of the test
     * @throws Exception if an unrecognized test condition is give, or if parsing or execution fails
     */
    private boolean parseTest() throws Exception{
        boolean invert = false;
        boolean out;
        if(tokenizer.peek().equals("!") || tokenizer.peek().equals("not")){
            tokenizer.next();
            invert = true;
        }

        String token = tokenizer.next();

        switch (token){
            case "leftclear":
                out = mCommandInterface.checkClear("LEFT");
                break;
            case "rightclear":
                out = mCommandInterface.checkClear("RIGHT");
                break;
            case "frontclear":
                out = mCommandInterface.checkClear("FRONT");
                break;
            case "backclear":
                out = mCommandInterface.checkClear("BACK");
                break;
            case "seejem":
                out = mCommandInterface.seeGem();
                break;
            case "hasjem":
                out = mCommandInterface.hasGem();
                break;
            case "facingN":
                out = mCommandInterface.checkFacing("UP");
                break;
            case "facingW":
                out = mCommandInterface.checkFacing("LEFT");
                break;
            case "facingS":
                out = mCommandInterface.checkFacing("DOWN");
                break;
            case "facingE":
                out = mCommandInterface.checkFacing("RIGHT");
                break;
            default:
                throw new Exception(String.format("Bad Condition: \"%s\" is not a recognized test condition", token));
        }
        if(invert)return !out;
        return out;
    }

    /**
     * Within the EBNF the definition of 'commands' and a single 'command' is different.
     * Commands are defined by a single command, followed by any additional commands, ended with a ";".
     * <p>The purpose of this phrasing is to show that if multiple singular commands are written on the same line,
     * a semicolon is only needed at the end of the line, rather than after every command
     * @throws Exception if parsing or execution fails
     */
    private void parseCommands() throws Exception {
        while(!tokenizer.peek().equals(";")){
            parseCommand(tokenizer.next());
        }
        tokenizer.eat(";");
    }

    /**
     * A single command is defined as any single instance of the following:
     * <p>
     * "step" | "turnL" | "turnR" | "take" | "drop"
     * <p>
     * Each of these commands corresponds to an action robbie will take in the environment
     * @param token the token to parse
     * @throws Exception if an unrecognized command is parsed, or if command execution fails
     */
    private void parseCommand(String token) throws Exception {
        switch (token){
            case "step":
                mCommandInterface.step();
                break;
            case "turnL":
                mCommandInterface.turnLeft();
                break;
            case "turnR":
                mCommandInterface.turnRight();
                break;
            case "take":
                mCommandInterface.take();
                break;
            case "drop":
                mCommandInterface.drop();
                break;
            default:
                throw new Exception(String.format("Invalid Command: \"%s\"", token));
        }
    }

    /**
     * A name is defined by the following regex: [A-Za-z_]+.
     * <p>
     * Names are used in the definition and calling of procedures
     * @return the parsed name
     * @throws Exception if the captured token doesn't match the format for a name.
     */
    private String parseName() throws Exception{
        String name = tokenizer.next();
        //because tokenizer ignores whitespace and errors at EOF, it is unnecessary to check if name is empty
        if(!name.matches("[A-Za-z_]+")){
            throw new Exception(String.format("Bad Name: \"%s\", names may only contain alphabetical characters and underscores", name));
        }
        return name;
    }

}
