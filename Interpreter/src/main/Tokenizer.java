package main;

import java.io.IOException;
import java.io.RandomAccessFile;


/**
 * This class provides functionality to break down robbie script files into a stream of keywords and key characters
 * and separates them into individual tokens as an in-between step for interpreting instructions.
 * <p>
 * This uses a blind queue system for interacting with tokens in the file, with *peek*, *eat*, and *next* commands
 * to interact with the token at the head of the queue.
 * <p>
 * To clarify, only a single token can be accessed from the file at a time, and this class tokenizes keywords
 * one token at a time as they are called from the "queue".
 *
 * @author Jared Schimpf
*/

public class Tokenizer {
    RandomAccessFile file;

    /**
     * Constructor
     *
     * @param file The file to be read, which contains the robbie instruction script
     * @throws java.lang.Exception if the constructor parameter is bad
     */
    public Tokenizer(RandomAccessFile file) throws Exception {
        if(file == null) throw new Exception("Passed in file cannot be null");
        this.file = file;
    }

    /**
     * Removes the current token from the queue and checks it against an expected string
     * <p>
     * Like guessing the top card of a deck before drawing it, losing the game if you were wrong.
     * @param str the expected string, this value is compared with the token returned from the queue
     * @throws Exception if the token doesn't match the expected string
     */
    public void eat(String str) throws Exception {
        String token = next();
        if(!token.equals(str)) throw new Exception(
                String.format("Unrecognized Keyword: expected \"%s\" but found \"%s\" instead", str, token));
    }

    /**
     * Returns the value of the current token from the head of the queue without consuming it.
     * <p>
     * Like looking at the top card of a deck without drawing it, or more accurately:
     * drawing it and putting it back.
     * @return The value of the current token string
     * @throws Exception if the current token is empty or EOF is reached
     */
    public String peek() throws Exception {
        long ptr = file.getFilePointer();
        String token = nextToken();
        file.seek(ptr);
        return token;
    }

    /**
     * Returns the current token string from the head of the queue, consuming it.
     * <p>
     * Like drawing a card, once you can see it, its no longer in your deck (the queue)
     * @return the current token string
     * @throws Exception if the token is empty, meaning the EOF has been reached
     */
    public String next() throws Exception {
        String token =  nextToken();
        if(token.isEmpty()) throw new Exception( "Format Error: missing \'}\', reached end of file while attempting to parse");
        return token;
    }

    /**
     * Internal method that checks the characters following the current file pointer for a valid token and returns it.
     * This partially avoids whitespace. Any whitespace before a token starts is skipped over, but whitespace is
     * used to denote the end of a token.
     * This method also filters out comments. comments follow the same syntax as java comments, and are described in the EBNF.
     * <p>
     * A valid token is any string of alphanumeric characters, or the following:
     * ';', '{', '}','!', '(', ')', ',',
     * <p> or a string: which is any valid string of characters, including escape characters, delimited by quotes.
     * More details on the nextString and evalEsc methods.
     * @return the next token string found in the file
     * @throws Exception dependent on helper methods, see evalEsc and nextString for more info.
     */

    //This could be simplified with regex, but when I wrote this my experience with it was very limited.
    //I'll leave it as is.
    private String nextToken() throws Exception {
        while(Character.isWhitespace(peekChar())){
            nextChar();
        }

        //check special character tokens
        switch (peekChar()){
            case ';', '{', '}','!', '(', ')', ',':
                return String.valueOf(nextChar());
        }

        if(peekChar() == '\"' ){
            return nextString();
        }

        if(peekChar() == '/' &&  isComment()){
            return nextToken();
        }

        return grabKeyword();
    }

    /**
     * Internal method that handles string tokens when an '"' is found by the NextToken method.
     * This requires different logic then capturing a token. A string can contain characters that are invalid in a normal token,
     * or which otherwise denote the end of a token.
     * This method also must check for escape characters within a string, which is handled by the evalEsc helper method.
     *
     * @return The body of the string as a token, without the surrounding quotes
     * @throws Exception if EOL or EOF is reached, or if an unrecognized escaped character is given
     */
    private String nextString() throws Exception {
        long ptr = file.getFilePointer();
        StringBuilder stringBuilder = new StringBuilder(String.valueOf(nextChar()));
        char curr = nextChar();
        while( curr != '\"'){

            //no EOL in string body
            if( curr == '\n'){
                throw new Exception(String.format("Bad String: end of line reached while trying to parse *%s*",stringBuilder.toString()));
            }

            //EOF error
            if( curr == '\uFFFF'){
                throw new Exception("Bad String: end of file reached while trying to parse string");
            }

            //denotes start of escape character
            if(curr == '\\'){
                curr = evalEsc(stringBuilder);
            }
            else {
                stringBuilder.append(curr);
                curr = nextChar();
            }
        }
        stringBuilder.append(curr);
        return stringBuilder.toString();
    }

    /**
     * Internal helper method for nextString that interprets the \n, \t, \b, \r, \f, \\, \", and \' escaped characters
     * @param stringBuilder passed in stringbuilder instance used by the nextString method to build the token.
     *                      Required to append interpreted escape character to the string token.
     * @return the character following the escaped character
     * @throws Exception if the escaped character is unrecognized
     */
    private char evalEsc(StringBuilder stringBuilder) throws Exception {
        char next = peekChar();
        switch (next){
            case 'n':
                nextChar();
                stringBuilder.append('\n');
                return nextChar();
            case 't':
                nextChar();
                stringBuilder.append('\t');
                return nextChar();
            case 'b':
                nextChar();
                stringBuilder.append('\b');
                return nextChar();
            case 'r':
                nextChar();
                stringBuilder.append('\r');
                return nextChar();
            case 'f':
                nextChar();
                stringBuilder.append('\f');
                return nextChar();
            case '\\':
                nextChar();
                stringBuilder.append('\\');
                return nextChar();
            case '\"':
                nextChar();
                stringBuilder.append('\"');
                return nextChar();
            case '\'':
                nextChar();
                stringBuilder.append('\'');
                return nextChar();
            default:
                throw new Exception(String.format("Bad Escape Character: could not recognize \"\\%c\"",next));
        }
    }

    /**
     * Internal helper method that grabs a valid keyword token (any string of alphanumeric characters)
     * @return the next valid keyword token
     * @throws IOException if an I/O error occurs with getFilePointer
     */
    private String grabKeyword() throws IOException{
        StringBuilder stringBuilder = new StringBuilder();


        long ptr = file.getFilePointer();
        while(peekChar() != ';' && peekChar() != '{' && peekChar() != '}' && !Character.isWhitespace(peekChar()) && peekChar() != '\uFFFF' ){
            stringBuilder.append(nextChar());
        }

        return stringBuilder.toString();
    }

    /**
     * Internal helper method used to check if a token starting with '/' is a comment,
     * in which case it is ignored and skipped over by the tokenizer
     * @return Boolean value: True if comment, otherwise False
     * @throws IOException if an I/O error occurs with getFilePointer
     */
    private boolean isComment()throws IOException{
        //need to move past the first / in order to peak to the next char
        long ptr = file.getFilePointer();
        nextChar();

        if(peekChar() == '/'){
            while(peekChar()!= '\n'){
                nextChar();
            }
            nextChar();
            return true;
        }

        if(peekChar() == '*'){
            char curr;
            do {
                curr = nextChar();
            }while(curr != '*' || peekChar() != '/');
            nextChar();
            return true;
        }
        //if we've reached this point then there was no comment,
        //We move back to before the first '/' so that it doesn't get skipped over.
        file.seek(ptr);
        return false;

    }

    /**
     * Internal helper method similar to peek, looks at the next character without moving past it.
     * @return the next char
     * @throws IOException if an I/O error occurs with getFilePointer
     */
    private char peekChar() throws IOException {
        long ptr = file.getFilePointer();
        char next = nextChar();
        file.seek(ptr);
        return next;
    }

    /**
     * Internal helper method similar to next, returns the next char in the file, then moves past it.
     * @return the next char
     * @throws IOException if an I/O error occurs with file.Read
     */
    private char nextChar() throws IOException {
        return (char)file.read();
    }
}
