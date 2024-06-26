package org.argouml.language.java.generator;

import java.util.Vector;

import java.io.InputStream;
import antlr.TokenStreamException;
import antlr.TokenStreamIOException;
import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.CharStreamIOException;
import antlr.ANTLRException;
import java.io.Reader;
import java.util.Hashtable;
import antlr.CharScanner;
import antlr.InputBuffer;
import antlr.ByteBuffer;
import antlr.CharBuffer;
import antlr.Token;
import antlr.CommonToken;
import antlr.RecognitionException;
import antlr.NoViableAltForCharException;
import antlr.MismatchedCharException;
import antlr.TokenStream;
import antlr.ANTLRHashString;
import antlr.LexerSharedInputState;
import antlr.collections.impl.BitSet;
import antlr.SemanticException;

class IdentifierHandler extends BaseLexer {
    Hashtable literals;

    public IdentifierHandler(LexerSharedInputState state, Hashtable literals) {
        super(state);
        this.literals = literals;
    }

    public final void mIDENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
        int _ttype; Token _token=null; int _begin=text.length();
        _ttype = IDENT;
        int _saveIndex;
        
        {
        switch ( LA(1)) {
        case 'a':  case 'b':  case 'c':  case 'd':
        case 'e':  case 'f':  case 'g':  case 'h':
        case 'i':  case 'j':  case 'k':  case 'l':
        case 'm':  case 'n':  case 'o':  case 'p':
        case 'q':  case 'r':  case 's':  case 't':
        case 'u':  case 'v':  case 'w':  case 'x':
        case 'y':  case 'z':
        {
            matchRange('a','z');
            break;
        }
        case 'A':  case 'B':  case 'C':  case 'D':
        case 'E':  case 'F':  case 'G':  case 'H':
        case 'I':  case 'J':  case 'K':  case 'L':
        case 'M':  case 'N':  case 'O':  case 'P':
        case 'Q':  case 'R':  case 'S':  case 'T':
        case 'U':  case 'V':  case 'W':  case 'X':
        case 'Y':  case 'Z':
        {
            matchRange('A','Z');
            break;
        }
        case '_':
        {
            match('_');
            break;
        }
        case '$':
        {
            match('$');
            break;
        }
        default:
        {
            throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine());
        }
        }
        }
        {
        _loop292:
        do {
            switch ( LA(1)) {
            case 'a':  case 'b':  case 'c':  case 'd':
            case 'e':  case 'f':  case 'g':  case 'h':
            case 'i':  case 'j':  case 'k':  case 'l':
            case 'm':  case 'n':  case 'o':  case 'p':
            case 'q':  case 'r':  case 's':  case 't':
            case 'u':  case 'v':  case 'w':  case 'x':
            case 'y':  case 'z':
            {
                matchRange('a','z');
                break;
            }
            case 'A':  case 'B':  case 'C':  case 'D':
            case 'E':  case 'F':  case 'G':  case 'H':
            case 'I':  case 'J':  case 'K':  case 'L':
            case 'M':  case 'N':  case 'O':  case 'P':
            case 'Q':  case 'R':  case 'S':  case 'T':
            case 'U':  case 'V':  case 'W':  case 'X':
            case 'Y':  case 'Z':
            {
                matchRange('A','Z');
                break;
            }
            case '_':
            {
                match('_');
                break;
            }
            case '0':  case '1':  case '2':  case '3':
            case '4':  case '5':  case '6':  case '7':
            case '8':  case '9':
            {
                matchRange('0','9');
                break;
            }
            case '$':
            {
                match('$');
                break;
            }
            default:
            {
                break _loop292;
            }
            }
        } while (true);
        }
        _ttype = testLiteralsTable(_ttype);
        if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
            _token = makeToken(_ttype);
            _token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
        }
        _returnToken = _token;
    }
}