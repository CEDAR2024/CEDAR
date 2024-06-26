class RegexPattern{
    public String getPattern() {
        return this.regex;
    }

    /**
     * Represents this instence in String.
     */
    public String toString() {
        return this.tokentree.toString(this.options);
    }

    /**
     * Returns a option string.
     * The order of letters in it may be different from a string specified
     * in a constructor or <code>setPattern()</code>.
     *
     * @see #RegularExpression(java.lang.String,java.lang.String)
     * @see #setPattern(java.lang.String,java.lang.String)
     */
    public String getOptions() {
        return REUtil.createOptionString(this.options);
    }

    /**
     *  Return true if patterns are the same and the options are equivalent.
     */
    public boolean equals(Object obj) {
        if (obj == null)  return false;
        if (!(obj instanceof RegularExpression))
            return false;
        RegularExpression r = (RegularExpression)obj;
        return this.regex.equals(r.regex) && this.options == r.options;
    }

    boolean equals(String pattern, int options) {
        return this.regex.equals(pattern) && this.options == options;
    }

    /**
     *
     */
    public int hashCode() {
        return (this.regex+"/"+this.getOptions()).hashCode();
    }
}
class RegexCompiler{
    /**
     * Converts a token to an operation.
     */
    private Op compile(Token tok, Op next, boolean reverse) {
        Op ret;
        switch (tok.type) {
            case Token.DOT:
                ret = Op.createDot();
                ret.next = next;
                break;

            case Token.CHAR:
                ret = Op.createChar(tok.getChar());
                ret.next = next;
                break;

            case Token.ANCHOR:
                ret = Op.createAnchor(tok.getChar());
                ret.next = next;
                break;

            case Token.RANGE:
            case Token.NRANGE:
                ret = Op.createRange(tok);
                ret.next = next;
                break;

            case Token.CONCAT:
                ret = next;
                if (!reverse) {
                    for (int i = tok.size()-1;  i >= 0;  i --) {
                        ret = compile(tok.getChild(i), ret, false);
                    }
                } else {
                    for (int i = 0;  i < tok.size();  i ++) {
                        ret = compile(tok.getChild(i), ret, true);
                    }
                }
                break;

            case Token.UNION:
                Op.UnionOp uni = Op.createUnion(tok.size());
                for (int i = 0;  i < tok.size();  i ++) {
                    uni.addElement(compile(tok.getChild(i), next, reverse));
                }
                ret = uni;                          // ret.next is null.
                break;

            case Token.CLOSURE:
            case Token.NONGREEDYCLOSURE:
                Token child = tok.getChild(0);
                int min = tok.getMin();
                int max = tok.getMax();
                if (min >= 0 && min == max) { // {n}
                    ret = next;
                    for (int i = 0; i < min;  i ++) {
                        ret = compile(child, ret, reverse);
                    }
                    break;
                }
                if (min > 0 && max > 0)
                    max -= min;
                if (max > 0) {
                    // X{2,6} -> XX(X(X(XX?)?)?)?
                    ret = next;
                    for (int i = 0;  i < max;  i ++) {
                        Op.ChildOp q = Op.createQuestion(tok.type == Token.NONGREEDYCLOSURE);
                        q.next = next;
                        q.setChild(compile(child, ret, reverse));
                        ret = q;
                    }
                } else {
                    Op.ChildOp op;
                    if (tok.type == Token.NONGREEDYCLOSURE) {
                        op = Op.createNonGreedyClosure();
                    } else {                        // Token.CLOSURE
                        if (child.getMinLength() == 0)
                            op = Op.createClosure(this.numberOfClosures++);
                        else
                            op = Op.createClosure(-1);
                    }
                    op.next = next;
                    op.setChild(compile(child, op, reverse));
                    ret = op;
                }
                if (min > 0) {
                    for (int i = 0;  i < min;  i ++) {
                        ret = compile(child, ret, reverse);
                    }
                }
                break;

            case Token.EMPTY:
                ret = next;
                break;

            case Token.STRING:
                ret = Op.createString(tok.getString());
                ret.next = next;
                break;

            case Token.BACKREFERENCE:
                ret = Op.createBackReference(tok.getReferenceNumber());
                ret.next = next;
                break;

            case Token.PAREN:
                if (tok.getParenNumber() == 0) {
                    ret = compile(tok.getChild(0), next, reverse);
                } else if (reverse) {
                    next = Op.createCapture(tok.getParenNumber(), next);
                    next = compile(tok.getChild(0), next, reverse);
                    ret = Op.createCapture(-tok.getParenNumber(), next);
                } else {
                    next = Op.createCapture(-tok.getParenNumber(), next);
                    next = compile(tok.getChild(0), next, reverse);
                    ret = Op.createCapture(tok.getParenNumber(), next);
                }
                break;

            case Token.LOOKAHEAD:
                ret = Op.createLook(Op.LOOKAHEAD, next, compile(tok.getChild(0), null, false));
                break;
            case Token.NEGATIVELOOKAHEAD:
                ret = Op.createLook(Op.NEGATIVELOOKAHEAD, next, compile(tok.getChild(0), null, false));
                break;
            case Token.LOOKBEHIND:
                ret = Op.createLook(Op.LOOKBEHIND, next, compile(tok.getChild(0), null, true));
                break;
            case Token.NEGATIVELOOKBEHIND:
                ret = Op.createLook(Op.NEGATIVELOOKBEHIND, next, compile(tok.getChild(0), null, true));
                break;

            case Token.INDEPENDENT:
                ret = Op.createIndependent(next, compile(tok.getChild(0), null, reverse));
                break;

            case Token.MODIFIERGROUP:
                ret = Op.createModifier(next, compile(tok.getChild(0), null, reverse),
                        ((Token.ModifierToken)tok).getOptions(),
                        ((Token.ModifierToken)tok).getOptionsMask());
                break;

            case Token.CONDITION:
                Token.ConditionToken ctok = (Token.ConditionToken)tok;
                int ref = ctok.refNumber;
                Op condition = ctok.condition == null ? null : compile(ctok.condition, null, reverse);
                Op yes = compile(ctok.yes, next, reverse);
                Op no = ctok.no == null ? null : compile(ctok.no, next, reverse);
                ret = Op.createCondition(next, ref, condition, yes, no);
                break;

            default:
                throw new RuntimeException("Unknown token type: "+tok.type);
        } // switch (tok.type)
        return ret;
    }

    /**
     * Prepares for matching.  This method is called just before starting matching.
     */
    void prepare() {
        if (Op.COUNT)  Op.nofinstances = 0;
        this.compile(this.tokentree);
        /*
        if  (this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) { // .*
            Op anchor = Op.createAnchor(isSet(this.options, SINGLE_LINE) ? 'A' : '@');
            anchor.next = this.operations;
            this.operations = anchor;
        }
        */
        if (Op.COUNT)  System.err.println("DEBUG: The number of operations: "+Op.nofinstances);

        this.minlength = this.tokentree.getMinLength();

        this.firstChar = null;
        if (!isSet(this.options, PROHIBIT_HEAD_CHARACTER_OPTIMIZATION)
                && !isSet(this.options, XMLSCHEMA_MODE)) {
            RangeToken firstChar = Token.createRange();
            int fresult = this.tokentree.analyzeFirstCharacter(firstChar, this.options);
            if (fresult == Token.FC_TERMINAL) {
                firstChar.compactRanges();
                this.firstChar = firstChar;
                if (DEBUG)
                    System.err.println("DEBUG: Use the first character optimization: "+firstChar);
            }
        }

        if (this.operations != null
                && (this.operations.type == Op.STRING || this.operations.type == Op.CHAR)
                && this.operations.next == null) {
            if (DEBUG)
                System.err.print(" *** Only fixed string! *** ");
            this.fixedStringOnly = true;
            if (this.operations.type == Op.STRING)
                this.fixedString = this.operations.getString();
            else if (this.operations.getData() >= 0x10000) { // Op.CHAR
                this.fixedString = REUtil.decomposeToSurrogates(this.operations.getData());
            } else {
                char[] ac = new char[1];
                ac[0] = (char)this.operations.getData();
                this.fixedString = new String(ac);
            }
            this.fixedStringOptions = this.options;
            this.fixedStringTable = new BMPattern(this.fixedString, 256,
                    isSet(this.fixedStringOptions, IGNORE_CASE));
        } else if (!isSet(this.options, PROHIBIT_FIXED_STRING_OPTIMIZATION)
                && !isSet(this.options, XMLSCHEMA_MODE)) {
            Token.FixedStringContainer container = new Token.FixedStringContainer();
            this.tokentree.findFixedString(container, this.options);
            this.fixedString = container.token == null ? null : container.token.getString();
            this.fixedStringOptions = container.options;
            if (this.fixedString != null && this.fixedString.length() < 2)
                this.fixedString = null;
            // This pattern has a fixed string of which length is more than one.
            if (this.fixedString != null) {
                this.fixedStringTable = new BMPattern(this.fixedString, 256,
                        isSet(this.fixedStringOptions, IGNORE_CASE));
                if (DEBUG) {
                    System.err.println("DEBUG: The longest fixed string: "+this.fixedString.length()
                            +"/" //+this.fixedString
                            +"/"+REUtil.createOptionString(this.fixedStringOptions));
                    System.err.print("String: ");
                    REUtil.dumpString(this.fixedString);
                }
            }
        }
    }
    /**
     *
     */
    public void setPattern(String newPattern) throws ParseException {
        this.setPattern(newPattern, this.options);
    }

    private void setPattern(String newPattern, int options) throws ParseException {
        this.regex = newPattern;
        this.options = options;
        RegexParser rp = this.isSet(this.options, RegularExpression.XMLSCHEMA_MODE)
                ? new ParserForXMLSchema() : new RegexParser();
        this.tokentree = rp.parse(this.regex, this.options);
        this.nofparen = rp.parennumber;
        this.hasBackReferences = rp.hasBackReferences;

        this.operations = null;
        this.context = null;
    }
    /**
     *
     */
    public void setPattern(String newPattern, String options) throws ParseException {
        this.setPattern(newPattern, REUtil.parseOptions(options));
    }
}

class CharArrayMatcher{
    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(char[]  target) {
        return this.matches(target, 0,  target .length , (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(char[]  target, int start, int end) {
        return this.matches(target, start, end, (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(char[]  target, Match match) {
        return this.matches(target, 0,  target .length , match);
    }


    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(char[]  target, int start, int end, Match match) {

        synchronized (this) {
            if (this.operations == null)
                this.prepare();
            if (this.context == null)
                this.context = new Context();
        }
        Context con = null;
        synchronized (this.context) {
            con = this.context.inuse ? new Context() : this.context;
            con.reset(target, start, end, this.numberOfClosures);
        }
        if (match != null) {
            match.setNumberOfGroups(this.nofparen);
            match.setSource(target);
        } else if (this.hasBackReferences) {
            match = new Match();
            match.setNumberOfGroups(this.nofparen);
            // Need not to call setSource() because
            // a caller can not access this match instance.
        }
        con.match = match;

        if (this.isSet(this.options, XMLSCHEMA_MODE)) {
            int matchEnd = this. matchCharArray (con, this.operations, con.start, 1, this.options);
            //System.err.println("DEBUG: matchEnd="+matchEnd);
            if (matchEnd == con.limit) {
                if (con.match != null) {
                    con.match.setBeginning(0, con.start);
                    con.match.setEnd(0, matchEnd);
                }
                con.inuse = false;
                return true;
            }
            return false;
        }

        /*
         * The pattern has only fixed string.
         * The engine uses Boyer-Moore.
         */
        if (this.fixedStringOnly) {
            //System.err.println("DEBUG: fixed-only: "+this.fixedString);
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o >= 0) {
                if (con.match != null) {
                    con.match.setBeginning(0, o);
                    con.match.setEnd(0, o+this.fixedString.length());
                }
                con.inuse = false;
                return true;
            }
            con.inuse = false;
            return false;
        }

        /*
         * The pattern contains a fixed string.
         * The engine checks with Boyer-Moore whether the text contains the fixed string or not.
         * If not, it return with false.
         */
        if (this.fixedString != null) {
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o < 0) {
                //System.err.println("Non-match in fixed-string search.");
                con.inuse = false;
                return false;
            }
        }

        int limit = con.limit-this.minlength;
        int matchStart;
        int matchEnd = -1;

        /*
         * Checks whether the expression starts with ".*".
         */
        if (this.operations != null
                && this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) {
            if (isSet(this.options, SINGLE_LINE)) {
                matchStart = con.start;
                matchEnd = this. matchCharArray (con, this.operations, con.start, 1, this.options);
            } else {
                boolean previousIsEOL = true;
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target [  matchStart ] ;
                    if (isEOLChar(ch)) {
                        previousIsEOL = true;
                    } else {
                        if (previousIsEOL) {
                            if (0 <= (matchEnd = this. matchCharArray (con, this.operations,
                                    matchStart, 1, this.options)))
                                break;
                        }
                        previousIsEOL = false;
                    }
                }
            }
        }

        /*
         * Optimization against the first character.
         */
        else if (this.firstChar != null) {
            //System.err.println("DEBUG: with firstchar-matching: "+this.firstChar);
            RangeToken range = this.firstChar;
            if (this.isSet(this.options, IGNORE_CASE)) {
                range = this.firstChar.getCaseInsensitiveToken();
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target [  matchStart ] ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit) {
                        ch = REUtil.composeFromSurrogates(ch,  target [  matchStart+1 ] );
                        if (!range.match(ch))  continue;
                    } else {
                        if (!range.match(ch)) {
                            char ch1 = Character.toUpperCase((char)ch);
                            if (!range.match(ch1))
                                if (!range.match(Character.toLowerCase(ch1)))
                                    continue;
                        }
                    }
                    if (0 <= (matchEnd = this. matchCharArray (con, this.operations,
                            matchStart, 1, this.options)))
                        break;
                }
            } else {
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target [  matchStart ] ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target [  matchStart+1 ] );
                    if (!range.match(ch))  continue;
                    if (0 <= (matchEnd = this. matchCharArray (con, this.operations,
                            matchStart, 1, this.options)))
                        break;
                }
            }
        }

        /*
         * Straightforward matching.
         */
        else {
            for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                if (0 <= (matchEnd = this. matchCharArray (con, this.operations, matchStart, 1, this.options)))
                    break;
            }
        }

        if (matchEnd >= 0) {
            if (con.match != null) {
                con.match.setBeginning(0, matchStart);
                con.match.setEnd(0, matchEnd);
            }
            con.inuse = false;
            return true;
        } else {
            con.inuse = false;
            return false;
        }
    }

    /**
     * @return -1 when not match; offset of the end of matched string when match.
     */
    private int matchCharArray (Context con, Op op, int offset, int dx, int opts) {

        char[] target = con.charTarget;


        while (true) {
            if (op == null)
                return offset;
            if (offset > con.limit || offset < con.start)
                return -1;
            switch (op.type) {
                case Op.CHAR:
                    if (isSet(opts, IGNORE_CASE)) {
                        int ch = op.getData();
                        if (dx > 0) {
                            if (offset >= con.limit || !matchIgnoreCase(ch,  target [  offset ] ))
                                return -1;
                            offset ++;
                        } else {
                            int o1 = offset-1;
                            if (o1 >= con.limit || o1 < 0 || !matchIgnoreCase(ch,  target [  o1 ] ))
                                return -1;
                            offset = o1;
                        }
                    } else {
                        int ch = op.getData();
                        if (dx > 0) {
                            if (offset >= con.limit || ch !=  target [  offset ] )
                                return -1;
                            offset ++;
                        } else {
                            int o1 = offset-1;
                            if (o1 >= con.limit || o1 < 0 || ch !=  target [  o1 ] )
                                return -1;
                            offset = o1;
                        }
                    }
                    op = op.next;
                    break;

                case Op.DOT:
                    if (dx > 0) {
                        if (offset >= con.limit)
                            return -1;
                        int ch =  target [  offset ] ;
                        if (isSet(opts, SINGLE_LINE)) {
                            if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                                offset ++;
                        } else {
                            if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                                ch = REUtil.composeFromSurrogates(ch,  target [  ++offset ] );
                            if (isEOLChar(ch))
                                return -1;
                        }
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0)
                            return -1;
                        int ch =  target [  o1 ] ;
                        if (isSet(opts, SINGLE_LINE)) {
                            if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                                o1 --;
                        } else {
                            if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                                ch = REUtil.composeFromSurrogates( target [  --o1 ] , ch);
                            if (!isEOLChar(ch))
                                return -1;
                        }
                        offset = o1;
                    }
                    op = op.next;
                    break;

                case Op.RANGE:
                case Op.NRANGE:
                    if (dx > 0) {
                        if (offset >= con.limit)
                            return -1;
                        int ch =  target [  offset ] ;
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            ch = REUtil.composeFromSurrogates(ch,  target [  ++offset ] );
                        RangeToken tok = op.getToken();
                        if (isSet(opts, IGNORE_CASE)) {
                            tok = tok.getCaseInsensitiveToken();
                            if (!tok.match(ch)) {
                                if (ch >= 0x10000)  return -1;
                                char uch;
                                if (!tok.match(uch = Character.toUpperCase((char)ch))
                                        && !tok.match(Character.toLowerCase(uch)))
                                    return -1;
                            }
                        } else {
                            if (!tok.match(ch))  return -1;
                        }
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0)
                            return -1;
                        int ch =  target [  o1 ] ;
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            ch = REUtil.composeFromSurrogates( target [  --o1 ] , ch);
                        RangeToken tok = op.getToken();
                        if (isSet(opts, IGNORE_CASE)) {
                            tok = tok.getCaseInsensitiveToken();
                            if (!tok.match(ch)) {
                                if (ch >= 0x10000)  return -1;
                                char uch;
                                if (!tok.match(uch = Character.toUpperCase((char)ch))
                                        && !tok.match(Character.toLowerCase(uch)))
                                    return -1;
                            }
                        } else {
                            if (!tok.match(ch))  return -1;
                        }
                        offset = o1;
                    }
                    op = op.next;
                    break;

                case Op.ANCHOR:
                    boolean go = false;
                    switch (op.getData()) {
                        case '^':
                            if (isSet(opts, MULTIPLE_LINES)) {
                                if (!(offset == con.start
                                        || offset > con.start && isEOLChar( target [  offset-1 ] )))
                                    return -1;
                            } else {
                                if (offset != con.start)
                                    return -1;
                            }
                            break;

                        case '@':                         // Internal use only.
                            // The @ always matches line beginnings.
                            if (!(offset == con.start
                                    || offset > con.start && isEOLChar( target [  offset-1 ] )))
                                return -1;
                            break;

                        case '$':
                            if (isSet(opts, MULTIPLE_LINES)) {
                                if (!(offset == con.limit
                                        || offset < con.limit && isEOLChar( target [  offset ] )))
                                    return -1;
                            } else {
                                if (!(offset == con.limit
                                        || offset+1 == con.limit && isEOLChar( target [  offset ] )
                                        || offset+2 == con.limit &&  target [  offset ]  == CARRIAGE_RETURN
                                        &&  target [  offset+1 ]  == LINE_FEED))
                                    return -1;
                            }
                            break;

                        case 'A':
                            if (offset != con.start)  return -1;
                            break;

                        case 'Z':
                            if (!(offset == con.limit
                                    || offset+1 == con.limit && isEOLChar( target [  offset ] )
                                    || offset+2 == con.limit &&  target [  offset ]  == CARRIAGE_RETURN
                                    &&  target [  offset+1 ]  == LINE_FEED))
                                return -1;
                            break;

                        case 'z':
                            if (offset != con.limit)  return -1;
                            break;

                        case 'b':
                            if (con.length == 0)  return -1;
                        {
                            int after = getWordType(target, con.start, con.limit, offset, opts);
                            if (after == WT_IGNORE)  return -1;
                            int before = getPreviousWordType(target, con.start, con.limit, offset, opts);
                            if (after == before)  return -1;
                        }
                        break;

                        case 'B':
                            if (con.length == 0)
                                go = true;
                            else {
                                int after = getWordType(target, con.start, con.limit, offset, opts);
                                go = after == WT_IGNORE
                                        || after == getPreviousWordType(target, con.start, con.limit, offset, opts);
                            }
                            if (!go)  return -1;
                            break;

                        case '<':
                            if (con.length == 0 || offset == con.limit)  return -1;
                            if (getWordType(target, con.start, con.limit, offset, opts) != WT_LETTER
                                    || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_OTHER)
                                return -1;
                            break;

                        case '>':
                            if (con.length == 0 || offset == con.start)  return -1;
                            if (getWordType(target, con.start, con.limit, offset, opts) != WT_OTHER
                                    || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_LETTER)
                                return -1;
                            break;
                    } // switch anchor type
                    op = op.next;
                    break;

                case Op.BACKREFERENCE:
                {
                    int refno = op.getData();
                    if (refno <= 0 || refno >= this.nofparen)
                        throw new RuntimeException("Internal Error: Reference number must be more than zero: "+refno);
                    if (con.match.getBeginning(refno) < 0
                            || con.match.getEnd(refno) < 0)
                        return -1;                // ********
                    int o2 = con.match.getBeginning(refno);
                    int literallen = con.match.getEnd(refno)-o2;
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                    o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;
                case Op.STRING:
                {
                    String literal = op.getString();
                    int literallen = literal.length();
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                    literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;

                case Op.CLOSURE:
                {
                    /*
                     * Saves current position to avoid
                     * zero-width repeats.
                     */
                    int id = op.getData();
                    if (id >= 0) {
                        int previousOffset = con.offsets[id];
                        if (previousOffset < 0 || previousOffset != offset) {
                            con.offsets[id] = offset;
                        } else {
                            con.offsets[id] = -1;
                            op = op.next;
                            break;
                        }
                    }

                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, opts);
                    if (id >= 0)  con.offsets[id] = -1;
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

                case Op.QUESTION:
                {
                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

                case Op.NONGREEDYCLOSURE:
                case Op.NONGREEDYQUESTION:
                {
                    int ret = this. matchCharArray (con, op.next, offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.getChild();
                }
                break;

                case Op.UNION:
                    for (int i = 0;  i < op.size();  i ++) {
                        int ret = this. matchCharArray (con, op.elementAt(i), offset, dx, opts);
                        //System.err.println("UNION: "+i+", ret="+ret);
                        if (ret >= 0)  return ret;
                    }
                    return -1;

                case Op.CAPTURE:
                    int refno = op.getData();
                    if (con.match != null && refno > 0) {
                        int save = con.match.getBeginning(refno);
                        con.match.setBeginning(refno, offset);
                        int ret = this. matchCharArray (con, op.next, offset, dx, opts);
                        if (ret < 0)  con.match.setBeginning(refno, save);
                        return ret;
                    } else if (con.match != null && refno < 0) {
                        int index = -refno;
                        int save = con.match.getEnd(index);
                        con.match.setEnd(index, offset);
                        int ret = this. matchCharArray (con, op.next, offset, dx, opts);
                        if (ret < 0)  con.match.setEnd(index, save);
                        return ret;
                    }
                    op = op.next;
                    break;

                case Op.LOOKAHEAD:
                    if (0 > this. matchCharArray (con, op.getChild(), offset, 1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.NEGATIVELOOKAHEAD:
                    if (0 <= this. matchCharArray (con, op.getChild(), offset, 1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.LOOKBEHIND:
                    if (0 > this. matchCharArray (con, op.getChild(), offset, -1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.NEGATIVELOOKBEHIND:
                    if (0 <= this. matchCharArray (con, op.getChild(), offset, -1, opts))  return -1;
                    op = op.next;
                    break;

                case Op.INDEPENDENT:
                {
                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, opts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

                case Op.MODIFIER:
                {
                    int localopts = opts;
                    localopts |= op.getData();
                    localopts &= ~op.getData2();
                    //System.err.println("MODIFIER: "+Integer.toString(opts, 16)+" -> "+Integer.toString(localopts, 16));
                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, localopts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

                case Op.CONDITION:
                {
                    Op.ConditionOp cop = (Op.ConditionOp)op;
                    boolean matchp = false;
                    if (cop.refNumber > 0) {
                        if (cop.refNumber >= this.nofparen)
                            throw new RuntimeException("Internal Error: Reference number must be more than zero: "+cop.refNumber);
                        matchp = con.match.getBeginning(cop.refNumber) >= 0
                                && con.match.getEnd(cop.refNumber) >= 0;
                    } else {
                        matchp = 0 <= this. matchCharArray (con, cop.condition, offset, dx, opts);
                    }

                    if (matchp) {
                        op = cop.yes;
                    } else if (cop.no != null) {
                        op = cop.no;
                    } else {
                        op = cop.next;
                    }
                }
                break;

                default:
                    throw new RuntimeException("Unknown operation type: "+op.type);
            } // switch (op.type)
        } // while
    }
}

class StringMatcher{
    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(String  target) {
        return this.matches(target, 0,  target .length() , (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(String  target, int start, int end) {
        return this.matches(target, start, end, (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(String  target, Match match) {
        return this.matches(target, 0,  target .length() , match);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(String  target, int start, int end, Match match) {

        synchronized (this) {
            if (this.operations == null)
                this.prepare();
            if (this.context == null)
                this.context = new Context();
        }
        Context con = null;
        synchronized (this.context) {
            con = this.context.inuse ? new Context() : this.context;
            con.reset(target, start, end, this.numberOfClosures);
        }
        if (match != null) {
            match.setNumberOfGroups(this.nofparen);
            match.setSource(target);
        } else if (this.hasBackReferences) {
            match = new Match();
            match.setNumberOfGroups(this.nofparen);
            // Need not to call setSource() because
            // a caller can not access this match instance.
        }
        con.match = match;

        if (this.isSet(this.options, XMLSCHEMA_MODE)) {
            int matchEnd = this. matchString (con, this.operations, con.start, 1, this.options);
            //System.err.println("DEBUG: matchEnd="+matchEnd);
            if (matchEnd == con.limit) {
                if (con.match != null) {
                    con.match.setBeginning(0, con.start);
                    con.match.setEnd(0, matchEnd);
                }
                con.inuse = false;
                return true;
            }
            return false;
        }

        /*
         * The pattern has only fixed string.
         * The engine uses Boyer-Moore.
         */
        if (this.fixedStringOnly) {
            //System.err.println("DEBUG: fixed-only: "+this.fixedString);
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o >= 0) {
                if (con.match != null) {
                    con.match.setBeginning(0, o);
                    con.match.setEnd(0, o+this.fixedString.length());
                }
                con.inuse = false;
                return true;
            }
            con.inuse = false;
            return false;
        }

        /*
         * The pattern contains a fixed string.
         * The engine checks with Boyer-Moore whether the text contains the fixed string or not.
         * If not, it return with false.
         */
        if (this.fixedString != null) {
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o < 0) {
                //System.err.println("Non-match in fixed-string search.");
                con.inuse = false;
                return false;
            }
        }

        int limit = con.limit-this.minlength;
        int matchStart;
        int matchEnd = -1;

        /*
         * Checks whether the expression starts with ".*".
         */
        if (this.operations != null
                && this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) {
            if (isSet(this.options, SINGLE_LINE)) {
                matchStart = con.start;
                matchEnd = this. matchString (con, this.operations, con.start, 1, this.options);
            } else {
                boolean previousIsEOL = true;
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .charAt(  matchStart ) ;
                    if (isEOLChar(ch)) {
                        previousIsEOL = true;
                    } else {
                        if (previousIsEOL) {
                            if (0 <= (matchEnd = this. matchString (con, this.operations,
                                    matchStart, 1, this.options)))
                                break;
                        }
                        previousIsEOL = false;
                    }
                }
            }
        }

        /*
         * Optimization against the first character.
         */
        else if (this.firstChar != null) {
            //System.err.println("DEBUG: with firstchar-matching: "+this.firstChar);
            RangeToken range = this.firstChar;
            if (this.isSet(this.options, IGNORE_CASE)) {
                range = this.firstChar.getCaseInsensitiveToken();
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .charAt(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit) {
                        ch = REUtil.composeFromSurrogates(ch,  target .charAt(  matchStart+1 ) );
                        if (!range.match(ch))  continue;
                    } else {
                        if (!range.match(ch)) {
                            char ch1 = Character.toUpperCase((char)ch);
                            if (!range.match(ch1))
                                if (!range.match(Character.toLowerCase(ch1)))
                                    continue;
                        }
                    }
                    if (0 <= (matchEnd = this. matchString (con, this.operations,
                            matchStart, 1, this.options)))
                        break;
                }
            } else {
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .charAt(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target .charAt(  matchStart+1 ) );
                    if (!range.match(ch))  continue;
                    if (0 <= (matchEnd = this. matchString (con, this.operations,
                            matchStart, 1, this.options)))
                        break;
                }
            }
        }

        /*
         * Straightforward matching.
         */
        else {
            for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                if (0 <= (matchEnd = this. matchString (con, this.operations, matchStart, 1, this.options)))
                    break;
            }
        }

        if (matchEnd >= 0) {
            if (con.match != null) {
                con.match.setBeginning(0, matchStart);
                con.match.setEnd(0, matchEnd);
            }
            con.inuse = false;
            return true;
        } else {
            con.inuse = false;
            return false;
        }
    }

    /**
     * @return -1 when not match; offset of the end of matched string when match.
     */
    private int matchString (Context con, Op op, int offset, int dx, int opts) {




        String target = con.strTarget;




        while (true) {
            if (op == null)
                return offset;
            if (offset > con.limit || offset < con.start)
                return -1;
            switch (op.type) {
                case Op.CHAR:
                    if (isSet(opts, IGNORE_CASE)) {
                        int ch = op.getData();
                        if (dx > 0) {
                            if (offset >= con.limit || !matchIgnoreCase(ch,  target .charAt(  offset ) ))
                                return -1;
                            offset ++;
                        } else {
                            int o1 = offset-1;
                            if (o1 >= con.limit || o1 < 0 || !matchIgnoreCase(ch,  target .charAt(  o1 ) ))
                                return -1;
                            offset = o1;
                        }
                    } else {
                        int ch = op.getData();
                        if (dx > 0) {
                            if (offset >= con.limit || ch !=  target .charAt(  offset ) )
                                return -1;
                            offset ++;
                        } else {
                            int o1 = offset-1;
                            if (o1 >= con.limit || o1 < 0 || ch !=  target .charAt(  o1 ) )
                                return -1;
                            offset = o1;
                        }
                    }
                    op = op.next;
                    break;

                case Op.DOT:
                    if (dx > 0) {
                        if (offset >= con.limit)
                            return -1;
                        int ch =  target .charAt(  offset ) ;
                        if (isSet(opts, SINGLE_LINE)) {
                            if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                                offset ++;
                        } else {
                            if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                                ch = REUtil.composeFromSurrogates(ch,  target .charAt(  ++offset ) );
                            if (isEOLChar(ch))
                                return -1;
                        }
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0)
                            return -1;
                        int ch =  target .charAt(  o1 ) ;
                        if (isSet(opts, SINGLE_LINE)) {
                            if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                                o1 --;
                        } else {
                            if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                                ch = REUtil.composeFromSurrogates( target .charAt(  --o1 ) , ch);
                            if (!isEOLChar(ch))
                                return -1;
                        }
                        offset = o1;
                    }
                    op = op.next;
                    break;

                case Op.RANGE:
                case Op.NRANGE:
                    if (dx > 0) {
                        if (offset >= con.limit)
                            return -1;
                        int ch =  target .charAt(  offset ) ;
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            ch = REUtil.composeFromSurrogates(ch,  target .charAt(  ++offset ) );
                        RangeToken tok = op.getToken();
                        if (isSet(opts, IGNORE_CASE)) {
                            tok = tok.getCaseInsensitiveToken();
                            if (!tok.match(ch)) {
                                if (ch >= 0x10000)  return -1;
                                char uch;
                                if (!tok.match(uch = Character.toUpperCase((char)ch))
                                        && !tok.match(Character.toLowerCase(uch)))
                                    return -1;
                            }
                        } else {
                            if (!tok.match(ch))  return -1;
                        }
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0)
                            return -1;
                        int ch =  target .charAt(  o1 ) ;
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            ch = REUtil.composeFromSurrogates( target .charAt(  --o1 ) , ch);
                        RangeToken tok = op.getToken();
                        if (isSet(opts, IGNORE_CASE)) {
                            tok = tok.getCaseInsensitiveToken();
                            if (!tok.match(ch)) {
                                if (ch >= 0x10000)  return -1;
                                char uch;
                                if (!tok.match(uch = Character.toUpperCase((char)ch))
                                        && !tok.match(Character.toLowerCase(uch)))
                                    return -1;
                            }
                        } else {
                            if (!tok.match(ch))  return -1;
                        }
                        offset = o1;
                    }
                    op = op.next;
                    break;

                case Op.ANCHOR:
                    boolean go = false;
                    switch (op.getData()) {
                        case '^':
                            if (isSet(opts, MULTIPLE_LINES)) {
                                if (!(offset == con.start
                                        || offset > con.start && isEOLChar( target .charAt(  offset-1 ) )))
                                    return -1;
                            } else {
                                if (offset != con.start)
                                    return -1;
                            }
                            break;

                        case '@':                         // Internal use only.
                            // The @ always matches line beginnings.
                            if (!(offset == con.start
                                    || offset > con.start && isEOLChar( target .charAt(  offset-1 ) )))
                                return -1;
                            break;

                        case '$':
                            if (isSet(opts, MULTIPLE_LINES)) {
                                if (!(offset == con.limit
                                        || offset < con.limit && isEOLChar( target .charAt(  offset ) )))
                                    return -1;
                            } else {
                                if (!(offset == con.limit
                                        || offset+1 == con.limit && isEOLChar( target .charAt(  offset ) )
                                        || offset+2 == con.limit &&  target .charAt(  offset )  == CARRIAGE_RETURN
                                        &&  target .charAt(  offset+1 )  == LINE_FEED))
                                    return -1;
                            }
                            break;

                        case 'A':
                            if (offset != con.start)  return -1;
                            break;

                        case 'Z':
                            if (!(offset == con.limit
                                    || offset+1 == con.limit && isEOLChar( target .charAt(  offset ) )
                                    || offset+2 == con.limit &&  target .charAt(  offset )  == CARRIAGE_RETURN
                                    &&  target .charAt(  offset+1 )  == LINE_FEED))
                                return -1;
                            break;

                        case 'z':
                            if (offset != con.limit)  return -1;
                            break;

                        case 'b':
                            if (con.length == 0)  return -1;
                        {
                            int after = getWordType(target, con.start, con.limit, offset, opts);
                            if (after == WT_IGNORE)  return -1;
                            int before = getPreviousWordType(target, con.start, con.limit, offset, opts);
                            if (after == before)  return -1;
                        }
                        break;

                        case 'B':
                            if (con.length == 0)
                                go = true;
                            else {
                                int after = getWordType(target, con.start, con.limit, offset, opts);
                                go = after == WT_IGNORE
                                        || after == getPreviousWordType(target, con.start, con.limit, offset, opts);
                            }
                            if (!go)  return -1;
                            break;

                        case '<':
                            if (con.length == 0 || offset == con.limit)  return -1;
                            if (getWordType(target, con.start, con.limit, offset, opts) != WT_LETTER
                                    || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_OTHER)
                                return -1;
                            break;

                        case '>':
                            if (con.length == 0 || offset == con.start)  return -1;
                            if (getWordType(target, con.start, con.limit, offset, opts) != WT_OTHER
                                    || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_LETTER)
                                return -1;
                            break;
                    } // switch anchor type
                    op = op.next;
                    break;

                case Op.BACKREFERENCE:
                {
                    int refno = op.getData();
                    if (refno <= 0 || refno >= this.nofparen)
                        throw new RuntimeException("Internal Error: Reference number must be more than zero: "+refno);
                    if (con.match.getBeginning(refno) < 0
                            || con.match.getEnd(refno) < 0)
                        return -1;                // ********
                    int o2 = con.match.getBeginning(refno);
                    int literallen = con.match.getEnd(refno)-o2;
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                    o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;
                case Op.STRING:
                {
                    String literal = op.getString();
                    int literallen = literal.length();
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                    literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;

                case Op.CLOSURE:
                {
                    /*
                     * Saves current position to avoid
                     * zero-width repeats.
                     */
                    int id = op.getData();
                    if (id >= 0) {
                        int previousOffset = con.offsets[id];
                        if (previousOffset < 0 || previousOffset != offset) {
                            con.offsets[id] = offset;
                        } else {
                            con.offsets[id] = -1;
                            op = op.next;
                            break;
                        }
                    }

                    int ret = this. matchString (con, op.getChild(), offset, dx, opts);
                    if (id >= 0)  con.offsets[id] = -1;
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

                case Op.QUESTION:
                {
                    int ret = this. matchString (con, op.getChild(), offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

                case Op.NONGREEDYCLOSURE:
                case Op.NONGREEDYQUESTION:
                {
                    int ret = this. matchString (con, op.next, offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.getChild();
                }
                break;

                case Op.UNION:
                    for (int i = 0;  i < op.size();  i ++) {
                        int ret = this. matchString (con, op.elementAt(i), offset, dx, opts);
                        //System.err.println("UNION: "+i+", ret="+ret);
                        if (ret >= 0)  return ret;
                    }
                    return -1;

                case Op.CAPTURE:
                    int refno = op.getData();
                    if (con.match != null && refno > 0) {
                        int save = con.match.getBeginning(refno);
                        con.match.setBeginning(refno, offset);
                        int ret = this. matchString (con, op.next, offset, dx, opts);
                        if (ret < 0)  con.match.setBeginning(refno, save);
                        return ret;
                    } else if (con.match != null && refno < 0) {
                        int index = -refno;
                        int save = con.match.getEnd(index);
                        con.match.setEnd(index, offset);
                        int ret = this. matchString (con, op.next, offset, dx, opts);
                        if (ret < 0)  con.match.setEnd(index, save);
                        return ret;
                    }
                    op = op.next;
                    break;

                case Op.LOOKAHEAD:
                    if (0 > this. matchString (con, op.getChild(), offset, 1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.NEGATIVELOOKAHEAD:
                    if (0 <= this. matchString (con, op.getChild(), offset, 1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.LOOKBEHIND:
                    if (0 > this. matchString (con, op.getChild(), offset, -1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.NEGATIVELOOKBEHIND:
                    if (0 <= this. matchString (con, op.getChild(), offset, -1, opts))  return -1;
                    op = op.next;
                    break;

                case Op.INDEPENDENT:
                {
                    int ret = this. matchString (con, op.getChild(), offset, dx, opts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

                case Op.MODIFIER:
                {
                    int localopts = opts;
                    localopts |= op.getData();
                    localopts &= ~op.getData2();
                    //System.err.println("MODIFIER: "+Integer.toString(opts, 16)+" -> "+Integer.toString(localopts, 16));
                    int ret = this. matchString (con, op.getChild(), offset, dx, localopts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

                case Op.CONDITION:
                {
                    Op.ConditionOp cop = (Op.ConditionOp)op;
                    boolean matchp = false;
                    if (cop.refNumber > 0) {
                        if (cop.refNumber >= this.nofparen)
                            throw new RuntimeException("Internal Error: Reference number must be more than zero: "+cop.refNumber);
                        matchp = con.match.getBeginning(cop.refNumber) >= 0
                                && con.match.getEnd(cop.refNumber) >= 0;
                    } else {
                        matchp = 0 <= this. matchString (con, cop.condition, offset, dx, opts);
                    }

                    if (matchp) {
                        op = cop.yes;
                    } else if (cop.no != null) {
                        op = cop.no;
                    } else {
                        op = cop.next;
                    }
                }
                break;

                default:
                    throw new RuntimeException("Unknown operation type: "+op.type);
            } // switch (op.type)
        } // while
    }
}
class CharacterIteratorMatcher{
    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(CharacterIterator target) {
        return this.matches(target, (Match)null);
    }


    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(CharacterIterator  target, Match match) {
        int start = target.getBeginIndex();
        int end = target.getEndIndex();



        synchronized (this) {
            if (this.operations == null)
                this.prepare();
            if (this.context == null)
                this.context = new Context();
        }
        Context con = null;
        synchronized (this.context) {
            con = this.context.inuse ? new Context() : this.context;
            con.reset(target, start, end, this.numberOfClosures);
        }
        if (match != null) {
            match.setNumberOfGroups(this.nofparen);
            match.setSource(target);
        } else if (this.hasBackReferences) {
            match = new Match();
            match.setNumberOfGroups(this.nofparen);
            // Need not to call setSource() because
            // a caller can not access this match instance.
        }
        con.match = match;

        if (this.isSet(this.options, XMLSCHEMA_MODE)) {
            int matchEnd = this. matchCharacterIterator (con, this.operations, con.start, 1, this.options);
            //System.err.println("DEBUG: matchEnd="+matchEnd);
            if (matchEnd == con.limit) {
                if (con.match != null) {
                    con.match.setBeginning(0, con.start);
                    con.match.setEnd(0, matchEnd);
                }
                con.inuse = false;
                return true;
            }
            return false;
        }

        /*
         * The pattern has only fixed string.
         * The engine uses Boyer-Moore.
         */
        if (this.fixedStringOnly) {
            //System.err.println("DEBUG: fixed-only: "+this.fixedString);
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o >= 0) {
                if (con.match != null) {
                    con.match.setBeginning(0, o);
                    con.match.setEnd(0, o+this.fixedString.length());
                }
                con.inuse = false;
                return true;
            }
            con.inuse = false;
            return false;
        }

        /*
         * The pattern contains a fixed string.
         * The engine checks with Boyer-Moore whether the text contains the fixed string or not.
         * If not, it return with false.
         */
        if (this.fixedString != null) {
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o < 0) {
                //System.err.println("Non-match in fixed-string search.");
                con.inuse = false;
                return false;
            }
        }

        int limit = con.limit-this.minlength;
        int matchStart;
        int matchEnd = -1;

        /*
         * Checks whether the expression starts with ".*".
         */
        if (this.operations != null
                && this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) {
            if (isSet(this.options, SINGLE_LINE)) {
                matchStart = con.start;
                matchEnd = this. matchCharacterIterator (con, this.operations, con.start, 1, this.options);
            } else {
                boolean previousIsEOL = true;
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .setIndex(  matchStart ) ;
                    if (isEOLChar(ch)) {
                        previousIsEOL = true;
                    } else {
                        if (previousIsEOL) {
                            if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations,
                                    matchStart, 1, this.options)))
                                break;
                        }
                        previousIsEOL = false;
                    }
                }
            }
        }

        /*
         * Optimization against the first character.
         */
        else if (this.firstChar != null) {
            //System.err.println("DEBUG: with firstchar-matching: "+this.firstChar);
            RangeToken range = this.firstChar;
            if (this.isSet(this.options, IGNORE_CASE)) {
                range = this.firstChar.getCaseInsensitiveToken();
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .setIndex(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit) {
                        ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  matchStart+1 ) );
                        if (!range.match(ch))  continue;
                    } else {
                        if (!range.match(ch)) {
                            char ch1 = Character.toUpperCase((char)ch);
                            if (!range.match(ch1))
                                if (!range.match(Character.toLowerCase(ch1)))
                                    continue;
                        }
                    }
                    if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations,
                            matchStart, 1, this.options)))
                        break;
                }
            } else {
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .setIndex(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  matchStart+1 ) );
                    if (!range.match(ch))  continue;
                    if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations,
                            matchStart, 1, this.options)))
                        break;
                }
            }
        }

        /*
         * Straightforward matching.
         */
        else {
            for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations, matchStart, 1, this.options)))
                    break;
            }
        }

        if (matchEnd >= 0) {
            if (con.match != null) {
                con.match.setBeginning(0, matchStart);
                con.match.setEnd(0, matchEnd);
            }
            con.inuse = false;
            return true;
        } else {
            con.inuse = false;
            return false;
        }
    }

    /**
     * @return -1 when not match; offset of the end of matched string when match.
     */
    private int matchCharacterIterator (Context con, Op op, int offset, int dx, int opts) {


        CharacterIterator target = con.ciTarget;






        while (true) {
            if (op == null)
                return offset;
            if (offset > con.limit || offset < con.start)
                return -1;
            switch (op.type) {
                case Op.CHAR:
                    if (isSet(opts, IGNORE_CASE)) {
                        int ch = op.getData();
                        if (dx > 0) {
                            if (offset >= con.limit || !matchIgnoreCase(ch,  target .setIndex(  offset ) ))
                                return -1;
                            offset ++;
                        } else {
                            int o1 = offset-1;
                            if (o1 >= con.limit || o1 < 0 || !matchIgnoreCase(ch,  target .setIndex(  o1 ) ))
                                return -1;
                            offset = o1;
                        }
                    } else {
                        int ch = op.getData();
                        if (dx > 0) {
                            if (offset >= con.limit || ch !=  target .setIndex(  offset ) )
                                return -1;
                            offset ++;
                        } else {
                            int o1 = offset-1;
                            if (o1 >= con.limit || o1 < 0 || ch !=  target .setIndex(  o1 ) )
                                return -1;
                            offset = o1;
                        }
                    }
                    op = op.next;
                    break;

                case Op.DOT:
                    if (dx > 0) {
                        if (offset >= con.limit)
                            return -1;
                        int ch =  target .setIndex(  offset ) ;
                        if (isSet(opts, SINGLE_LINE)) {
                            if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                                offset ++;
                        } else {
                            if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                                ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  ++offset ) );
                            if (isEOLChar(ch))
                                return -1;
                        }
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0)
                            return -1;
                        int ch =  target .setIndex(  o1 ) ;
                        if (isSet(opts, SINGLE_LINE)) {
                            if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                                o1 --;
                        } else {
                            if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                                ch = REUtil.composeFromSurrogates( target .setIndex(  --o1 ) , ch);
                            if (!isEOLChar(ch))
                                return -1;
                        }
                        offset = o1;
                    }
                    op = op.next;
                    break;

                case Op.RANGE:
                case Op.NRANGE:
                    if (dx > 0) {
                        if (offset >= con.limit)
                            return -1;
                        int ch =  target .setIndex(  offset ) ;
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  ++offset ) );
                        RangeToken tok = op.getToken();
                        if (isSet(opts, IGNORE_CASE)) {
                            tok = tok.getCaseInsensitiveToken();
                            if (!tok.match(ch)) {
                                if (ch >= 0x10000)  return -1;
                                char uch;
                                if (!tok.match(uch = Character.toUpperCase((char)ch))
                                        && !tok.match(Character.toLowerCase(uch)))
                                    return -1;
                            }
                        } else {
                            if (!tok.match(ch))  return -1;
                        }
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0)
                            return -1;
                        int ch =  target .setIndex(  o1 ) ;
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            ch = REUtil.composeFromSurrogates( target .setIndex(  --o1 ) , ch);
                        RangeToken tok = op.getToken();
                        if (isSet(opts, IGNORE_CASE)) {
                            tok = tok.getCaseInsensitiveToken();
                            if (!tok.match(ch)) {
                                if (ch >= 0x10000)  return -1;
                                char uch;
                                if (!tok.match(uch = Character.toUpperCase((char)ch))
                                        && !tok.match(Character.toLowerCase(uch)))
                                    return -1;
                            }
                        } else {
                            if (!tok.match(ch))  return -1;
                        }
                        offset = o1;
                    }
                    op = op.next;
                    break;

                case Op.ANCHOR:
                    boolean go = false;
                    switch (op.getData()) {
                        case '^':
                            if (isSet(opts, MULTIPLE_LINES)) {
                                if (!(offset == con.start
                                        || offset > con.start && isEOLChar( target .setIndex(  offset-1 ) )))
                                    return -1;
                            } else {
                                if (offset != con.start)
                                    return -1;
                            }
                            break;

                        case '@':                         // Internal use only.
                            // The @ always matches line beginnings.
                            if (!(offset == con.start
                                    || offset > con.start && isEOLChar( target .setIndex(  offset-1 ) )))
                                return -1;
                            break;

                        case '$':
                            if (isSet(opts, MULTIPLE_LINES)) {
                                if (!(offset == con.limit
                                        || offset < con.limit && isEOLChar( target .setIndex(  offset ) )))
                                    return -1;
                            } else {
                                if (!(offset == con.limit
                                        || offset+1 == con.limit && isEOLChar( target .setIndex(  offset ) )
                                        || offset+2 == con.limit &&  target .setIndex(  offset )  == CARRIAGE_RETURN
                                        &&  target .setIndex(  offset+1 )  == LINE_FEED))
                                    return -1;
                            }
                            break;

                        case 'A':
                            if (offset != con.start)  return -1;
                            break;

                        case 'Z':
                            if (!(offset == con.limit
                                    || offset+1 == con.limit && isEOLChar( target .setIndex(  offset ) )
                                    || offset+2 == con.limit &&  target .setIndex(  offset )  == CARRIAGE_RETURN
                                    &&  target .setIndex(  offset+1 )  == LINE_FEED))
                                return -1;
                            break;

                        case 'z':
                            if (offset != con.limit)  return -1;
                            break;

                        case 'b':
                            if (con.length == 0)  return -1;
                        {
                            int after = getWordType(target, con.start, con.limit, offset, opts);
                            if (after == WT_IGNORE)  return -1;
                            int before = getPreviousWordType(target, con.start, con.limit, offset, opts);
                            if (after == before)  return -1;
                        }
                        break;

                        case 'B':
                            if (con.length == 0)
                                go = true;
                            else {
                                int after = getWordType(target, con.start, con.limit, offset, opts);
                                go = after == WT_IGNORE
                                        || after == getPreviousWordType(target, con.start, con.limit, offset, opts);
                            }
                            if (!go)  return -1;
                            break;

                        case '<':
                            if (con.length == 0 || offset == con.limit)  return -1;
                            if (getWordType(target, con.start, con.limit, offset, opts) != WT_LETTER
                                    || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_OTHER)
                                return -1;
                            break;

                        case '>':
                            if (con.length == 0 || offset == con.start)  return -1;
                            if (getWordType(target, con.start, con.limit, offset, opts) != WT_OTHER
                                    || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_LETTER)
                                return -1;
                            break;
                    } // switch anchor type
                    op = op.next;
                    break;

                case Op.BACKREFERENCE:
                {
                    int refno = op.getData();
                    if (refno <= 0 || refno >= this.nofparen)
                        throw new RuntimeException("Internal Error: Reference number must be more than zero: "+refno);
                    if (con.match.getBeginning(refno) < 0
                            || con.match.getEnd(refno) < 0)
                        return -1;                // ********
                    int o2 = con.match.getBeginning(refno);
                    int literallen = con.match.getEnd(refno)-o2;
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                    o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;
                case Op.STRING:
                {
                    String literal = op.getString();
                    int literallen = literal.length();
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                    literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;

                case Op.CLOSURE:
                {
                    /*
                     * Saves current position to avoid
                     * zero-width repeats.
                     */
                    int id = op.getData();
                    if (id >= 0) {
                        int previousOffset = con.offsets[id];
                        if (previousOffset < 0 || previousOffset != offset) {
                            con.offsets[id] = offset;
                        } else {
                            con.offsets[id] = -1;
                            op = op.next;
                            break;
                        }
                    }

                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, opts);
                    if (id >= 0)  con.offsets[id] = -1;
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

                case Op.QUESTION:
                {
                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

                case Op.NONGREEDYCLOSURE:
                case Op.NONGREEDYQUESTION:
                {
                    int ret = this. matchCharacterIterator (con, op.next, offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.getChild();
                }
                break;

                case Op.UNION:
                    for (int i = 0;  i < op.size();  i ++) {
                        int ret = this. matchCharacterIterator (con, op.elementAt(i), offset, dx, opts);
                        //System.err.println("UNION: "+i+", ret="+ret);
                        if (ret >= 0)  return ret;
                    }
                    return -1;

                case Op.CAPTURE:
                    int refno = op.getData();
                    if (con.match != null && refno > 0) {
                        int save = con.match.getBeginning(refno);
                        con.match.setBeginning(refno, offset);
                        int ret = this. matchCharacterIterator (con, op.next, offset, dx, opts);
                        if (ret < 0)  con.match.setBeginning(refno, save);
                        return ret;
                    } else if (con.match != null && refno < 0) {
                        int index = -refno;
                        int save = con.match.getEnd(index);
                        con.match.setEnd(index, offset);
                        int ret = this. matchCharacterIterator (con, op.next, offset, dx, opts);
                        if (ret < 0)  con.match.setEnd(index, save);
                        return ret;
                    }
                    op = op.next;
                    break;

                case Op.LOOKAHEAD:
                    if (0 > this. matchCharacterIterator (con, op.getChild(), offset, 1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.NEGATIVELOOKAHEAD:
                    if (0 <= this. matchCharacterIterator (con, op.getChild(), offset, 1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.LOOKBEHIND:
                    if (0 > this. matchCharacterIterator (con, op.getChild(), offset, -1, opts))  return -1;
                    op = op.next;
                    break;
                case Op.NEGATIVELOOKBEHIND:
                    if (0 <= this. matchCharacterIterator (con, op.getChild(), offset, -1, opts))  return -1;
                    op = op.next;
                    break;

                case Op.INDEPENDENT:
                {
                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, opts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

                case Op.MODIFIER:
                {
                    int localopts = opts;
                    localopts |= op.getData();
                    localopts &= ~op.getData2();
                    //System.err.println("MODIFIER: "+Integer.toString(opts, 16)+" -> "+Integer.toString(localopts, 16));
                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, localopts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

                case Op.CONDITION:
                {
                    Op.ConditionOp cop = (Op.ConditionOp)op;
                    boolean matchp = false;
                    if (cop.refNumber > 0) {
                        if (cop.refNumber >= this.nofparen)
                            throw new RuntimeException("Internal Error: Reference number must be more than zero: "+cop.refNumber);
                        matchp = con.match.getBeginning(cop.refNumber) >= 0
                                && con.match.getEnd(cop.refNumber) >= 0;
                    } else {
                        matchp = 0 <= this. matchCharacterIterator (con, cop.condition, offset, dx, opts);
                    }

                    if (matchp) {
                        op = cop.yes;
                    } else if (cop.no != null) {
                        op = cop.no;
                    } else {
                        op = cop.next;
                    }
                }
                break;

                default:
                    throw new RuntimeException("Unknown operation type: "+op.type);
            } // switch (op.type)
        } // while
    }
}
class RegexContextManager{
    public void resetCommon(int nofclosures) {
        this.length = this.limit-this.start;
        this.inuse = true;
        this.match = null;
        if (this.offsets == null || this.offsets.length != nofclosures)
            this.offsets = new int[nofclosures];
        for (int i = 0;  i < nofclosures;  i ++)  this.offsets[i] = -1;
    }

    void reset(CharacterIterator target, int start, int limit, int nofclosures) {
        this.ciTarget = target;
        this.start = start;
        this.limit = limit;
        this.resetCommon(nofclosures);
    }
    void reset(String target, int start, int limit, int nofclosures) {
        this.strTarget = target;
        this.start = start;
        this.limit = limit;
        this.resetCommon(nofclosures);
    }
    void reset(char[] target, int start, int limit, int nofclosures) {
        this.charTarget = target;
        this.start = start;
        this.limit = limit;
        this.resetCommon(nofclosures);
    }
}

class RegularExpression{
    public static final boolean isSet(int options, int flag) {
        return (options & flag) == flag;
    }

    /**
     * Creates a new RegularExpression instance.
     *
     * @param regex A regular expression
     * @exception org.apache.xerces.utils.regex.ParseException <VAR>regex</VAR> is not conforming to the syntax.
     */
    public RegularExpression(String regex) throws ParseException {
        this.setPattern(regex, null);
    }

    /**
     * Creates a new RegularExpression instance with options.
     *
     * @param regex A regular expression
     * @param options A String consisted of "i" "m" "s" "u" "w" "," "X"
     * @exception org.apache.xerces.utils.regex.ParseException <VAR>regex</VAR> is not conforming to the syntax.
     */
    public RegularExpression(String regex, String options) throws ParseException {
        this.setPattern(regex, options);
    }

    /**
     * Return the number of regular expression groups.
     * This method returns 1 when the regular expression has no capturing-parenthesis.
     *
     */
    public int getNumberOfGroups() {
        return this.nofparen;
    }
}
class WordTypeUtilities{
    public static final int getPreviousWordType(char[]  target, int begin, int end,
                                                 int offset, int opts) {
        int ret = getWordType(target, begin, end, --offset, opts);
        while (ret == WT_IGNORE)
            ret = getWordType(target, begin, end, --offset, opts);
        return ret;
    }

    public static final int getWordType(char[]  target, int begin, int end,
                                         int offset, int opts) {
        if (offset < begin || offset >= end)  return WT_OTHER;
        return getWordType0( target [  offset ] , opts);
    }

    public static final int getPreviousWordType(String  target, int begin, int end,
                                                 int offset, int opts) {
        int ret = getWordType(target, begin, end, --offset, opts);
        while (ret == WT_IGNORE)
            ret = getWordType(target, begin, end, --offset, opts);
        return ret;
    }

    public static final int getWordType(String  target, int begin, int end,
                                         int offset, int opts) {
        if (offset < begin || offset >= end)  return WT_OTHER;
        return getWordType0( target .charAt(  offset ) , opts);
    }

    public static final int getPreviousWordType(CharacterIterator  target, int begin, int end,
                                                 int offset, int opts) {
        int ret = getWordType(target, begin, end, --offset, opts);
        while (ret == WT_IGNORE)
            ret = getWordType(target, begin, end, --offset, opts);
        return ret;
    }

    public static final int getWordType(CharacterIterator  target, int begin, int end,
                                         int offset, int opts) {
        if (offset < begin || offset >= end)  return WT_OTHER;
        return getWordType0( target .setIndex(  offset ) , opts);
    }
}

class RegionMatchUtilities{
    private static final boolean regionMatches(char[]  target, int offset, int limit,
                                               String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            if ( target [  offset++ ]  != part.charAt(i++))
                return false;
        }
        return true;
    }

    private static final boolean regionMatches(char[]  target, int offset, int limit,
                                               int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            if ( target [  offset++ ]  !=  target [  i++ ] )
                return false;
        }
        return true;
    }

    /**
     * @see java.lang.String#regionMatches
     */
    private static final boolean regionMatchesIgnoreCase(char[]  target, int offset, int limit,
                                                         String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            char ch1 =  target [  offset++ ] ;
            char ch2 = part.charAt(i++);
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }

    private static final boolean regionMatchesIgnoreCase(char[]  target, int offset, int limit,
                                                         int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            char ch1 =  target [  offset++ ] ;
            char ch2 =  target [  i++ ] ;
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }

    private static final boolean regionMatches(String text, int offset, int limit,
                                               String part, int partlen) {
        if (limit-offset < partlen)  return false;
        return text.regionMatches(offset, part, 0, partlen);
    }

    private static final boolean regionMatches(String text, int offset, int limit,
                                               int offset2, int partlen) {
        if (limit-offset < partlen)  return false;
        return text.regionMatches(offset, text, offset2, partlen);
    }

    private static final boolean regionMatchesIgnoreCase(String text, int offset, int limit,
                                                         String part, int partlen) {
        return text.regionMatches(true, offset, part, 0, partlen);
    }

    private static final boolean regionMatchesIgnoreCase(String text, int offset, int limit,
                                                         int offset2, int partlen) {
        if (limit-offset < partlen)  return false;
        return text.regionMatches(true, offset, text, offset2, partlen);
    }

    private static final boolean regionMatches(CharacterIterator  target, int offset, int limit,
                                               String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            if ( target .setIndex(  offset++ )  != part.charAt(i++))
                return false;
        }
        return true;
    }

    private static final boolean regionMatches(CharacterIterator  target, int offset, int limit,
                                               int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            if ( target .setIndex(  offset++ )  !=  target .setIndex(  i++ ) )
                return false;
        }
        return true;
    }

    /**
     * @see java.lang.String#regionMatches
     */
    private static final boolean regionMatchesIgnoreCase(CharacterIterator  target, int offset, int limit,
                                                         String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            char ch1 =  target .setIndex(  offset++ ) ;
            char ch2 = part.charAt(i++);
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }

    private static final boolean regionMatchesIgnoreCase(CharacterIterator  target, int offset, int limit,
                                                         int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            char ch1 =  target .setIndex(  offset++ ) ;
            char ch2 =  target .setIndex(  i++ ) ;
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }
}
