/**********************************************************************
 * Copyright (c) 2002-2004 IBM Canada and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * IBM Rational Software - Initial API and implementation */
package org.eclipse.cdt.internal.core.parser2;

import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.parser.BacktrackException;
import org.eclipse.cdt.core.parser.EndOfFileException;
import org.eclipse.cdt.core.parser.IGCCToken;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IProblem;
import org.eclipse.cdt.core.parser.IScanner;
import org.eclipse.cdt.core.parser.IToken;
import org.eclipse.cdt.core.parser.OffsetLimitReachedException;
import org.eclipse.cdt.core.parser.ParseError;
import org.eclipse.cdt.core.parser.ParserMode;
import org.eclipse.cdt.internal.core.parser.Parser;
import org.eclipse.cdt.internal.core.parser.ParserProblemFactory;
import org.eclipse.cdt.internal.core.parser.Parser.Flags;
import org.eclipse.cdt.internal.core.parser.problem.IProblemFactory;
import org.eclipse.cdt.internal.core.parser.token.TokenFactory;
import org.eclipse.cdt.internal.core.parser2.cpp.IProblemRequestor;

/**
 * @author jcamelon
 */
public abstract class AbstractGNUSourceCodeParser implements ISourceCodeParser {

    protected final IParserLogService log;

    protected final IScanner scanner;

    protected final ParserMode mode;

    protected IProblemRequestor requestor = null;

    protected final boolean supportStatementsInExpressions;
    protected final boolean supportTypeOfUnaries;
    protected final boolean supportAlignOfUnaries;

    protected AbstractGNUSourceCodeParser(IScanner scanner, IParserLogService logService,
            ParserMode parserMode, IProblemRequestor callback,
            boolean supportStatementsInExpressions, boolean supportTypeOfUnaries, boolean supportAlignOfUnaries ) {
        this.scanner = scanner;
        this.log = logService;
        this.mode = parserMode;
        this.requestor = callback;
        this.supportStatementsInExpressions = supportStatementsInExpressions;
        this.supportTypeOfUnaries = supportTypeOfUnaries; 
        this.supportAlignOfUnaries = supportAlignOfUnaries;
    }

    protected boolean parsePassed = true;

    protected BacktrackException backtrack = new BacktrackException();

    protected int backtrackCount = 0;

    protected final void throwBacktrack(int startingOffset, int endingOffset,
            int lineNumber, char[] f) throws BacktrackException {
        ++backtrackCount;
        backtrack.initialize(startingOffset,
                (endingOffset == 0) ? startingOffset + 1 : endingOffset,
                lineNumber, f);
        throw backtrack;
    }

    protected IToken currToken;

    protected IToken lastToken;

    /**
     * Look Ahead in the token list to see what is coming.
     * 
     * @param i
     *            How far ahead do you wish to peek?
     * @return the token you wish to observe
     * @throws EndOfFileException
     *             if looking ahead encounters EOF, throw EndOfFile
     */
    protected IToken LA(int i) throws EndOfFileException {

        if (isCancelled) {
            throw new ParseError(ParseError.ParseErrorKind.TIMEOUT_OR_CANCELLED);
        }

        if (i < 1) // can't go backwards
            return null;
        if (currToken == null)
            currToken = fetchToken();
        IToken retToken = currToken;
        for (; i > 1; --i) {
            retToken = retToken.getNext();
            if (retToken == null)
                retToken = fetchToken();
        }
        return retToken;
    }

    /**
     * Look ahead in the token list and return the token type.
     * 
     * @param i
     *            How far ahead do you wish to peek?
     * @return The type of that token
     * @throws EndOfFileException
     *             if looking ahead encounters EOF, throw EndOfFile
     */
    protected int LT(int i) throws EndOfFileException {
        return LA(i).getType();
    }

    /**
     * Consume the next token available, regardless of the type.
     * 
     * @return The token that was consumed and removed from our buffer.
     * @throws EndOfFileException
     *             If there is no token to consume.
     */
    protected IToken consume() throws EndOfFileException {

        if (currToken == null)
            currToken = fetchToken();
        if (currToken != null)
            lastToken = currToken;
        currToken = currToken.getNext();
        return lastToken;
    }

    /**
     * Consume the next token available only if the type is as specified.
     * 
     * @param type
     *            The type of token that you are expecting.
     * @return the token that was consumed and removed from our buffer.
     * @throws BacktrackException
     *             If LT(1) != type
     */
    protected IToken consume(int type) throws EndOfFileException,
            BacktrackException {
        if (LT(1) == type)
            return consume();
        IToken la = LA(1);
        throwBacktrack(la.getOffset(), la.getEndOffset(), la.getLineNumber(),
                la.getFilename());
        return null;
    }

    /**
     * Fetches a token from the scanner.
     * 
     * @return the next token from the scanner
     * @throws EndOfFileException
     *             thrown when the scanner.nextToken() yields no tokens
     */
    protected IToken fetchToken() throws EndOfFileException {
        try {
            IToken value = scanner.nextToken();
            return value;
        } catch (OffsetLimitReachedException olre) {
            handleOffsetLimitException(olre);
            return null;
        }
    }

    protected boolean isCancelled = false;

    protected static final int DEFAULT_DESIGNATOR_LIST_SIZE = 4;

    protected IProblemFactory problemFactory = new ParserProblemFactory();

    protected static int parseCount = 0;

    protected void handleOffsetLimitException(
            OffsetLimitReachedException exception) throws EndOfFileException {
        if (mode != ParserMode.COMPLETION_PARSE)
            throw new EndOfFileException();
        throw exception;
    }

    protected static final char[] EMPTY_STRING = "".toCharArray(); //$NON-NLS-1$

    /**
     * Mark our place in the buffer so that we could return to it should we have
     * to.
     * 
     * @return The current token.
     * @throws EndOfFileException
     *             If there are no more tokens.
     */
    protected IToken mark() throws EndOfFileException {
        if (currToken == null)
            currToken = fetchToken();
        return currToken;
    }

    /**
     * Rollback to a previous point, reseting the queue of tokens.
     * 
     * @param mark
     *            The point that we wish to restore to.
     *  
     */
    protected void backup(IToken mark) {
        currToken = mark;
        lastToken = null; // this is not entirely right ...
    }

    /**
     * This is the single entry point for setting parsePassed to false, and also
     * making note what token offset we failed upon.
     * 
     * @throws EndOfFileException
     */
    protected void failParse() {
        parsePassed = false;
    }

    /**
     *  /* (non-Javadoc)
     * @see org.eclipse.cdt.core.parser.IParser#cancel()
     */
    public synchronized void cancel() {
        isCancelled = true;
        scanner.cancel();
    }

    /**
     * Parse an identifier.
     * 
     * @throws BacktrackException
     *             request a backtrack
     */
    protected IToken identifier() throws EndOfFileException, BacktrackException {
        return consume(IToken.tIDENTIFIER);
    }

    /**
     * @return Returns the backtrackCount.
     */
    public final int getBacktrackCount() {
        return backtrackCount;
    }

    /**
     * @param bt
     */
    protected void throwBacktrack(BacktrackException bt)
            throws BacktrackException {
        throw bt;
    }

    protected void failParse(BacktrackException bt) {
        if (requestor != null) {
            if (bt.getProblem() == null) {
                IProblem problem = problemFactory.createProblem(
                        IProblem.SYNTAX_ERROR, bt.getStartingOffset(), bt
                                .getEndOffset(), bt.getLineNumber(), bt
                                .getFilename(), EMPTY_STRING, false, true);
                requestor.acceptProblem(problem);
            } else
                requestor.acceptProblem(bt.getProblem());
        }
        failParse();
    }

    protected void failParse(IProblem problem) {
        if (problem != null && requestor != null) {
            requestor.acceptProblem(problem);
        }
        failParse();
    }

    /**
     * @param string
     * @param e
     */
    protected void logThrowable(String methodName, Throwable e) {
        if (e != null && log.isTracing()) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("Parser: Unexpected throwable in "); //$NON-NLS-1$
            buffer.append(methodName);
            buffer.append(":"); //$NON-NLS-1$
            buffer.append(e.getClass().getName());
            buffer.append("::"); //$NON-NLS-1$
            buffer.append(e.getMessage());
            buffer.append(". w/"); //$NON-NLS-1$
            buffer.append(scanner.toString());
            log.traceLog(buffer.toString());
            //			log.errorLog( buffer.toString() );
        }
    }

    public String toString() {
        return scanner.toString(); //$NON-NLS-1$
    }

    /**
     * @param methodName
     * @param e
     */
    protected void logException(String methodName, Exception e) {
        if (!(e instanceof EndOfFileException) && e != null && log.isTracing()) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("Parser: Unexpected exception in "); //$NON-NLS-1$
            buffer.append(methodName);
            buffer.append(":"); //$NON-NLS-1$
            buffer.append(e.getClass().getName());
            buffer.append("::"); //$NON-NLS-1$
            buffer.append(e.getMessage());
            buffer.append(". w/"); //$NON-NLS-1$
            buffer.append(scanner.toString());
            log.traceLog(buffer.toString());
            //			log.errorLog(buffer.toString());
        }
    }

    protected final void throwBacktrack(IProblem problem)
            throws BacktrackException {
        ++backtrackCount;
        backtrack.initialize(problem);
        throw backtrack;
    }

    protected Object compilationUnit;

    protected IToken simpleDeclarationMark;

    protected abstract void translationUnit();

    protected abstract void statement(Object scope) throws EndOfFileException,
            BacktrackException;

    /**
     *  
     */
    protected void cleanupLastToken() {
        if (lastToken != null)
            lastToken.setNext(null);
        simpleDeclarationMark = null;
    }

    public IASTTranslationUnit parse() {
        long startTime = System.currentTimeMillis();
        translationUnit();
        // For the debuglog to take place, you have to call
        // Util.setDebugging(true);
        // Or set debug to true in the core plugin preference
        log.traceLog("Parse " //$NON-NLS-1$
                + (++parseCount) + ": " //$NON-NLS-1$
                + (System.currentTimeMillis() - startTime) + "ms" //$NON-NLS-1$
                + (parsePassed ? "" : " - parse failure")); //$NON-NLS-1$ //$NON-NLS-2$
        return null;
    }

    protected void skipOverCompoundStatement() throws BacktrackException,
            EndOfFileException {
        // speed up the parser by skiping the body
        // simply look for matching brace and return
        consume(IToken.tLBRACE);
        int depth = 1;
        while (depth > 0) {
            switch (consume().getType()) {
            case IToken.tRBRACE:
                --depth;
                break;
            case IToken.tLBRACE:
                ++depth;
                break;
            }
        }
    }

    /**
     * @throws EndOfFileException
     */
    protected void errorHandling() throws EndOfFileException {
        int depth = (LT(1) == IToken.tLBRACE) ? 1 : 0;
        int type = consume().getType();
        if (type == IToken.tSEMI)
            return;
        while (!((LT(1) == IToken.tSEMI && depth == 0) || (LT(1) == IToken.tRBRACE && depth == 1))) {
            switch (LT(1)) {
            case IToken.tLBRACE:
                ++depth;
                break;
            case IToken.tRBRACE:
                --depth;
                break;
            }
            if (depth < 0)
                return;

            consume();
        }
        // eat the SEMI/RBRACE as well
        consume();
    }

    /**
     * This function is called whenever we encounter and error that we cannot
     * backtrack out of and we still wish to try and continue on with the parse
     * to do a best-effort parse for our client.
     * 
     * @throws EndOfFileException
     *             We can potentially hit EndOfFile here as we are skipping
     *             ahead.
     */
    protected void failParseWithErrorHandling() throws EndOfFileException {
        failParse();
        errorHandling();
    }

    /**
     * @param d
     */
    protected void throwAwayMarksForInitializerClause(Declarator d) {
        simpleDeclarationMark = null;
        if (d.getNameDuple() != null)
            d.getNameDuple().getLastToken().setNext(null);
        if (d.getPointerOperatorNameDuple() != null)
            d.getPointerOperatorNameDuple().getLastToken().setNext(null);
    }

    /**
     * @throws BacktrackException
     */
    protected void compoundStatement(Object scope, boolean createNewScope)
            throws EndOfFileException, BacktrackException {
        IToken la = LA(1);
        int line = la.getLineNumber();
        char[] fn = la.getFilename();
        int startingOffset = consume(IToken.tLBRACE).getOffset();

        Object newScope = null;
        if (createNewScope) {
            try {
                newScope = null; /* astFactory.createNewCodeBlock(scope); */
            } catch (Exception e) {
                int endOffset = (lastToken == null) ? 0 : lastToken
                        .getEndOffset();
                logException("compoundStatement:createNewCodeBlock", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, line, fn);
            }
            //			newScope.enterScope(requestor);
        }

        try {

            while (LT(1) != IToken.tRBRACE) {
                int checkToken = LA(1).hashCode();
                try {
                    statement((createNewScope ? newScope : scope));
                } catch (BacktrackException b) {
                    failParse(b);
                    if (LA(1).hashCode() == checkToken)
                        failParseWithErrorHandling();
                }
            }

            consume(IToken.tRBRACE);
        } finally {
            //			if (createNewScope)
            //				newScope.exitScope(requestor);
        }
    }

    /**
     * @param scope
     * @param la
     * @param resultExpression
     * @return @throws
     *         EndOfFileException
     * @throws BacktrackException
     */
    protected Object compoundStatementExpression(Object scope, IToken la,
            Object resultExpression) throws EndOfFileException,
            BacktrackException {
        int startingOffset = la.getOffset();
        int ln = la.getLineNumber();
        char[] fn = la.getFilename();
        consume(IToken.tLPAREN);
        try {
            if (mode == ParserMode.QUICK_PARSE
                    || mode == ParserMode.STRUCTURAL_PARSE)
                skipOverCompoundStatement();
            else if (mode == ParserMode.COMPLETION_PARSE
                    || mode == ParserMode.SELECTION_PARSE) {
                if (scanner.isOnTopContext())
                    compoundStatement(scope, true);
                else
                    skipOverCompoundStatement();
            } else if (mode == ParserMode.COMPLETE_PARSE)
                compoundStatement(scope, true);

            consume(IToken.tRPAREN);
            try {
                resultExpression = null; /*
                                          * astFactory.createExpression( scope,
                                          * IASTGCCExpression.Kind.STATEMENT_EXPRESSION,
                                          * null, null, null, null,
                                          * null,EMPTY_STRING, null );
                                          */
            }
            /*
             * catch (ASTSemanticException e) { throwBacktrack(e.getProblem()); }
             */catch (Exception e) {
                logException("expression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, lastToken != null ? lastToken
                        .getEndOffset() : 0, ln, fn);
            }
        } catch (BacktrackException bte) {
            backup(la);
        }
        return resultExpression;
    }

    protected Object expression(Object scope) throws BacktrackException,
            EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int ln = la.getLineNumber();
        char[] fn = la.getFilename();

        Object resultExpression = null;
        if (la.getType() == IToken.tLPAREN && LT(2) == IToken.tLBRACE
                && supportStatementsInExpressions) {
            resultExpression = compoundStatementExpression(scope, la,
                    resultExpression);
        }

        if (resultExpression != null)
            return resultExpression;

        Object assignmentExpression = assignmentExpression(scope);
        while (LT(1) == IToken.tCOMMA) {
            consume(IToken.tCOMMA);

            Object secondExpression = assignmentExpression(scope);

            int endOffset = lastToken != null ? lastToken.getEndOffset() : 0;
            try {
                assignmentExpression = null; /*
                                              * astFactory.createExpression(scope,
                                              * IASTExpression.Kind.EXPRESSIONLIST,
                                              * assignmentExpression,
                                              * secondExpression, null, null,
                                              * null, EMPTY_STRING, null);
                                              */
            } /*
               * catch (ASTSemanticException e) {
               * throwBacktrack(e.getProblem()); }
               */catch (Exception e) {
                logException("expression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, ln, fn);
            }
        }
        return assignmentExpression;
    }

    protected abstract Object assignmentExpression(Object scope)
            throws BacktrackException, EndOfFileException;

    protected abstract Object relationalExpression(Object scope)
            throws BacktrackException, EndOfFileException;

    protected abstract Object multiplicativeExpression(Object scope)
            throws BacktrackException, EndOfFileException;

    protected abstract Object typeId(Object scope, boolean skipArrayMods)
            throws BacktrackException, EndOfFileException;

    protected abstract Object castExpression(Object scope)
            throws BacktrackException, EndOfFileException;
    
    protected abstract Object unaryExpression(Object scope)
    throws BacktrackException, EndOfFileException;


    protected Object assignmentOperatorExpression(Object scope, Object kind,
            Object lhs) throws EndOfFileException, BacktrackException {
        IToken t = consume();
        Object assignmentExpression = assignmentExpression(scope);
        int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
        try {
            return null; /*
                          * astFactory.createExpression(scope, kind, lhs,
                          * assignmentExpression, null, null, null,
                          * EMPTY_STRING, null); } catch (ASTSemanticException
                          * e) { throwBacktrack(e.getProblem());
                          */
        } catch (Exception e) {
            logException("assignmentOperatorExpression::createExpression()", e); //$NON-NLS-1$
            throwBacktrack(t.getOffset(), endOffset, t.getLineNumber(), t
                    .getFilename());
        }
        return null;
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object constantExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        return conditionalExpression(scope);
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object logicalOrExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();
        Object firstExpression = logicalAndExpression(scope);
        while (LT(1) == IToken.tOR) {
            consume(IToken.tOR);
            Object secondExpression = logicalAndExpression(scope);
            int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
            try {
                firstExpression = null; /*
                                         * astFactory.createExpression(scope,
                                         * IASTExpression.Kind.LOGICALOREXPRESSION,
                                         * firstExpression, secondExpression,
                                         * null, null, null, EMPTY_STRING,
                                         * null); } catch (ASTSemanticException
                                         * e) { throwBacktrack(e.getProblem());
                                         */
            } catch (Exception e) {
                logException("logicalOrExpression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, line, fn);
            }
        }
        return firstExpression;
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object logicalAndExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();
        Object firstExpression = inclusiveOrExpression(scope);
        while (LT(1) == IToken.tAND) {
            consume(IToken.tAND);
            Object secondExpression = inclusiveOrExpression(scope);
            int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
            try {
                firstExpression = null; /*
                                         * astFactory.createExpression(scope,
                                         * IASTExpression.Kind.LOGICALANDEXPRESSION,
                                         * firstExpression, secondExpression,
                                         * null, null, null, EMPTY_STRING,
                                         * null); } catch (ASTSemanticException
                                         * e) { throwBacktrack(e.getProblem());
                                         */
            } catch (Exception e) {
                logException("logicalAndExpression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, line, fn);
            }
        }
        return firstExpression;
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object inclusiveOrExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();

        Object firstExpression = exclusiveOrExpression(scope);
        while (LT(1) == IToken.tBITOR) {
            consume();
            Object secondExpression = exclusiveOrExpression(scope);
            int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
            try {
                firstExpression = null; /*
                                         * astFactory.createExpression(scope,
                                         * IASTExpression.Kind.INCLUSIVEOREXPRESSION,
                                         * firstExpression, secondExpression,
                                         * null, null, null, EMPTY_STRING,
                                         * null); } catch (ASTSemanticException
                                         * e) { throwBacktrack(e.getProblem());
                                         */
            } catch (Exception e) {
                logException("inclusiveOrExpression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, line, fn);
            }
        }
        return firstExpression;
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object exclusiveOrExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();

        Object firstExpression = andExpression(scope);
        while (LT(1) == IToken.tXOR) {
            consume();

            Object secondExpression = andExpression(scope);
            int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
            try {
                firstExpression = null; /*
                                         * astFactory.createExpression(scope,
                                         * IASTExpression.Kind.EXCLUSIVEOREXPRESSION,
                                         * firstExpression, secondExpression,
                                         * null, null, null, EMPTY_STRING,
                                         * null); } catch (ASTSemanticException
                                         * e) { throwBacktrack(e.getProblem());
                                         */
            } catch (Exception e) {
                logException("exclusiveORExpression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, line, fn);
            }
        }
        return firstExpression;
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object andExpression(Object scope) throws EndOfFileException,
            BacktrackException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();

        Object firstExpression = equalityExpression(scope);
        while (LT(1) == IToken.tAMPER) {
            consume();
            Object secondExpression = equalityExpression(scope);
            int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
            try {
                firstExpression = null; /*
                                         * astFactory.createExpression(scope,
                                         * IASTExpression.Kind.ANDEXPRESSION,
                                         * firstExpression, secondExpression,
                                         * null, null, null, EMPTY_STRING,
                                         * null); } catch (ASTSemanticException
                                         * e) { throwBacktrack(e.getProblem());
                                         */
            } catch (Exception e) {
                logException("andExpression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, line, fn);
            }
        }
        return firstExpression;
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object equalityExpression(Object scope)
            throws EndOfFileException, BacktrackException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();

        Object firstExpression = relationalExpression(scope);
        for (;;) {
            switch (LT(1)) {
            case IToken.tEQUAL:
            case IToken.tNOTEQUAL:
                IToken t = consume();
                Object secondExpression = relationalExpression(scope);
                int endOffset = (lastToken != null) ? lastToken.getEndOffset()
                        : 0;
                try {
                    firstExpression = null; /*
                                             * astFactory.createExpression(scope,
                                             * (t .getType() == IToken.tEQUAL) ?
                                             * IASTExpression.Kind.EQUALITY_EQUALS :
                                             * IASTExpression.Kind.EQUALITY_NOTEQUALS,
                                             * firstExpression,
                                             * secondExpression, null, null,
                                             * null, EMPTY_STRING, null); }
                                             * catch (ASTSemanticException e) {
                                             * throwBacktrack(e.getProblem());
                                             */
                } catch (Exception e) {
                    logException("equalityExpression::createExpression()", e); //$NON-NLS-1$
                    throwBacktrack(startingOffset, endOffset, line, fn);
                }
                break;
            default:
                return firstExpression;
            }
        }
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object shiftExpression(Object scope) throws BacktrackException,
            EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();
        Object firstExpression = additiveExpression(scope);
        for (;;) {
            switch (LT(1)) {
            case IToken.tSHIFTL:
            case IToken.tSHIFTR:
                IToken t = consume();
                Object secondExpression = additiveExpression(scope);
                int endOffset = (lastToken != null) ? lastToken.getEndOffset()
                        : 0;
                try {
                    firstExpression = null; /*
                                             * astFactory.createExpression(scope,
                                             * ((t.getType() == IToken.tSHIFTL) ?
                                             * IASTExpression.Kind.SHIFT_LEFT :
                                             * IASTExpression.Kind.SHIFT_RIGHT),
                                             * firstExpression,
                                             * secondExpression, null, null,
                                             * null, EMPTY_STRING, null); }
                                             * catch (ASTSemanticException e) {
                                             * throwBacktrack(e.getProblem());
                                             */
                } catch (Exception e) {
                    logException("shiftExpression::createExpression()", e); //$NON-NLS-1$
                    throwBacktrack(startingOffset, endOffset, line, fn);
                }
                break;
            default:
                return firstExpression;
            }
        }
    }

    /**
     * @param expression
     * @throws BacktrackException
     */
    protected Object additiveExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();
        Object firstExpression = multiplicativeExpression(scope);
        for (;;) {
            switch (LT(1)) {
            case IToken.tPLUS:
            case IToken.tMINUS:
                IToken t = consume();
                Object secondExpression = multiplicativeExpression(scope);
                int endOffset = (lastToken != null) ? lastToken.getEndOffset()
                        : 0;
                try {
                    firstExpression = null; /*
                                             * astFactory.createExpression(scope,
                                             * ((t.getType() == IToken.tPLUS) ?
                                             * IASTExpression.Kind.ADDITIVE_PLUS :
                                             * IASTExpression.Kind.ADDITIVE_MINUS),
                                             * firstExpression,
                                             * secondExpression, null, null,
                                             * null, EMPTY_STRING, null); }
                                             * catch (ASTSemanticException e) {
                                             * throwBacktrack(e.getProblem());
                                             */
                } catch (Exception e) {
                    logException("additiveExpression::createExpression()", e); //$NON-NLS-1$
                    throwBacktrack(startingOffset, endOffset, line, fn);
                }
                break;
            default:
                return firstExpression;
            }
        }
    }

    /**
     * @param expression
     * @return @throws
     *         BacktrackException
     */
    protected Object conditionalExpression(Object scope)
            throws BacktrackException, EndOfFileException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int ln = la.getLineNumber();
        char[] fn = la.getFilename();

        Object firstExpression = logicalOrExpression(scope);
        if (LT(1) == IToken.tQUESTION) {
            consume(IToken.tQUESTION);
            Object secondExpression = expression(scope);
            consume(IToken.tCOLON);
            Object thirdExpression = assignmentExpression(scope);
            int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
            try {
                return null; /*
                              * astFactory.createExpression(scope,
                              * IASTExpression.Kind.CONDITIONALEXPRESSION,
                              * firstExpression, secondExpression,
                              * thirdExpression, null, null, EMPTY_STRING,
                              * null);
                              */
            } /*
               * catch (ASTSemanticException e) {
               * throwBacktrack(e.getProblem()); }
               */catch (Exception e) {
                logException("conditionalExpression::createExpression()", e); //$NON-NLS-1$
                throwBacktrack(startingOffset, endOffset, ln, fn);
            }
        }
        return firstExpression;
    }

    protected Object unaryOperatorCastExpression(Object scope, Object kind)
            throws EndOfFileException, BacktrackException {
        IToken la = LA(1);
        int startingOffset = la.getOffset();
        int line = la.getLineNumber();
        char[] fn = la.getFilename();
        la = null;
        Object castExpression = castExpression(scope);
        int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
        try {
            return null; /*
                          * astFactory.createExpression(scope, kind,
                          * castExpression, null, null, null, null,
                          * EMPTY_STRING, null); } catch (ASTSemanticException
                          * e) { throwBacktrack(e.getProblem());
                          */
        } catch (Exception e) {
            logException("unaryOperatorCastExpression::createExpression()", e); //$NON-NLS-1$
            throwBacktrack(startingOffset, endOffset, line, fn);
        }
        return null;
    }
    
    /**
     * @param scope
     * @return @throws
     *         BacktrackException
     * @throws EndOfFileException
     */
    protected Object unaryAlignofExpression(Object scope)
            throws EndOfFileException, BacktrackException {
        consume(IGCCToken.t___alignof__);
        Object d = null;
        Object unaryExpression = null;

        IToken m = mark();
        if (LT(1) == IToken.tLPAREN) {
            try {
                consume(IToken.tLPAREN);
                d = typeId(scope, false);
                consume(IToken.tRPAREN);
            } catch (BacktrackException bt) {
                backup(m);
                d = null;
                unaryExpression = unaryExpression(scope);
            }
        } else {
            unaryExpression = unaryExpression(scope);
        }
        if (d != null & unaryExpression == null) {
            //                try {
            return null; /*
                          * astFactory.createExpression( scope,
                          * IASTGCCExpression.Kind.UNARY_ALIGNOF_TYPEID, null,
                          * null, null, d, null, EMPTY_STRING, null); } catch
                          * (ASTSemanticException e2) { throwBacktrack(
                          * e2.getProblem() ); }
                          */
        } else if (unaryExpression != null && d == null)
            //            try
            //            {
            return null; /*
                          * .createExpression( scope,
                          * IASTGCCExpression.Kind.UNARY_ALIGNOF_UNARYEXPRESSION,
                          * unaryExpression, null, null, null, null,
                          * EMPTY_STRING, null); } catch (ASTSemanticException
                          * e1) { throwBacktrack( e1.getProblem() ); }
                          */
        return null;

    }

    protected Object unaryTypeofExpression(Object scope)
            throws EndOfFileException, BacktrackException {
        consume(IGCCToken.t_typeof);
        Object d = null;
        Object unaryExpression = null;

        IToken m = mark();
        if (LT(1) == IToken.tLPAREN) {
            try {
                consume(IToken.tLPAREN);
                d = typeId(scope, false);
                consume(IToken.tRPAREN);
            } catch (BacktrackException bt) {
                backup(m);
                d = null;
                unaryExpression = unaryExpression(scope);
            }
        } else {
            unaryExpression = unaryExpression(scope);
        }
        if (d != null & unaryExpression == null) {
            //                try {
            return null; /*
                          * astFactory.createExpression( scope,
                          * IASTGCCExpression.Kind.UNARY_TYPEOF_TYPEID, null,
                          * null, null, d, null, EMPTY_STRING, null); } catch
                          * (ASTSemanticException e2) { throwBacktrack(
                          * e2.getProblem() ); }
                          */
        } else if (unaryExpression != null && d == null)
            //            try
            //            {
            return null; /*
                          * astFactory.createExpression( scope,
                          * IASTGCCExpression.Kind.UNARY_TYPEOF_UNARYEXPRESSION,
                          * unaryExpression, null, null, null, null,
                          * EMPTY_STRING, null); } catch (ASTSemanticException
                          * e1) { throwBacktrack( e1.getProblem() ); }
                          */
        return null;
    }

    private TypeId typeIdInstance = new TypeId();

    /**
     * @param scope
     * @return
     */
    protected TypeId getTypeIdInstance(Object scope) {
        typeIdInstance.reset(scope);
        return typeIdInstance;
    }

    protected void handleFunctionBody(Object scope) throws BacktrackException, EndOfFileException {
        if (mode == ParserMode.QUICK_PARSE
                || mode == ParserMode.STRUCTURAL_PARSE)
            skipOverCompoundStatement();
        else if (mode == ParserMode.COMPLETION_PARSE
                || mode == ParserMode.SELECTION_PARSE) {
            if (scanner.isOnTopContext())
                functionBody(scope);
            else
                skipOverCompoundStatement();
        } else if (mode == ParserMode.COMPLETE_PARSE)
            functionBody(scope);
    
    }

    /**
     * Parses a function body.
     * 
     * @throws BacktrackException
     *             request a backtrack
     */
    protected void functionBody(Object scope) throws EndOfFileException, BacktrackException {
        compoundStatement(scope, false);
    }

    /**
     * @param sdw
     * @param typeNameBegin
     * @param typeNameEnd
     */
    protected void setTypeName(DeclarationWrapper sdw, IToken typeNameBegin, IToken typeNameEnd) {
        if (typeNameBegin != null)
            sdw.setTypeName(TokenFactory.createTokenDuple(typeNameBegin,
                    typeNameEnd));
    }

    /**
     * @param flags
     *            input flags that are used to make our decision
     * @return whether or not this looks like a a declarator follows
     * @throws EndOfFileException
     *             we could encounter EOF while looking ahead
     */
    protected boolean lookAheadForDeclarator(Flags flags) throws EndOfFileException {
        return flags.haveEncounteredTypename()
                && ((LT(2) != IToken.tIDENTIFIER || (LT(3) != IToken.tLPAREN && LT(3) != IToken.tASSIGN)) && !LA(
                        2).isPointer());
    }
    
    public static class Flags
    {
        private boolean encounteredTypename = false;
        // have we encountered a typeName yet?
        private boolean encounteredRawType = false;
        // have we encountered a raw type yet?
        private final boolean parm;
        // is this for a simpleDeclaration or parameterDeclaration?
        private final boolean constructor;
        // are we attempting the constructor strategy?
        public Flags(boolean parm, boolean c)
        {
            this.parm = parm;
            constructor = c;
        }
        
        public Flags( boolean parm )
        {
            this( parm, false );
        }
        /**
		 * @return true if we have encountered a simple type up to this point,
		 *         false otherwise
		 */
        public boolean haveEncounteredRawType()
        {
            return encounteredRawType;
        }
        /**
		 * @return true if we have encountered a typename up to this point,
		 *         false otherwise
		 */
        public boolean haveEncounteredTypename()
        {
            return encounteredTypename;
        }
        /**
		 * @param b -
		 *            set to true if we encounter a raw type (int, short, etc.)
		 */
        public void setEncounteredRawType(boolean b)
        {
            encounteredRawType = b;
        }
        /**
		 * @param b -
		 *            set to true if we encounter a typename
		 */
        public void setEncounteredTypename(boolean b)
        {
            encounteredTypename = b;
        }
        /**
		 * @return true if we are parsing for a ParameterDeclaration
		 */
        public boolean isForParameterDeclaration()
        {
            return parm;
        }
        /**
		 * @return whether or not we are attempting the constructor strategy or
		 *         not
		 */
        public boolean isForConstructor()
        {
            return constructor;
        }
    }

    /**
     * Parse an enumeration specifier, as according to the ANSI specs in C &
     * C++.
     * 
     * enumSpecifier: "enum" (name)? "{" (enumerator-list) "}" enumerator-list:
     * enumerator-definition enumerator-list , enumerator-definition
     * enumerator-definition: enumerator enumerator = constant-expression
     * enumerator: identifier
     * 
     * @param owner
     *            IParserCallback object that represents the declaration that
     *            owns this type specifier.
     * @throws BacktrackException
     *             request a backtrack
     */
    protected void enumSpecifier(DeclarationWrapper sdw) throws BacktrackException, EndOfFileException {
            IToken mark = mark();
            IToken identifier = null;
            consume(IToken.t_enum);
            if (LT(1) == IToken.tIDENTIFIER) {
                identifier = identifier();
            }
            if (LT(1) == IToken.tLBRACE) {
                Object enumeration = null;
                try {
                    enumeration = null; /*astFactory.createEnumerationSpecifier(sdw
                     .getScope(), ((identifier == null)
                     ? EMPTY_STRING : identifier.getCharImage()), //$NON-NLS-1$
                     mark.getOffset(), mark.getLineNumber(),
                     ((identifier == null) ? mark.getOffset() : identifier
                     .getOffset()), ((identifier == null) ? mark
                     .getEndOffset() : identifier.getEndOffset()),
                     ((identifier == null)
                     ? mark.getLineNumber()
                     : identifier.getLineNumber()), mark.getFilename());
                     } catch (ASTSemanticException e) {
                     throwBacktrack(e.getProblem()); */
                } catch (Exception e) {
                    int endOffset = (lastToken != null) ? lastToken.getEndOffset()
                            : 0;
                    logException("enumSpecifier:createEnumerationSpecifier", e); //$NON-NLS-1$
                    throwBacktrack(mark.getOffset(), endOffset, mark
                            .getLineNumber(), mark.getFilename());
                }
                cleanupLastToken();
                consume(IToken.tLBRACE);
                while (LT(1) != IToken.tRBRACE) {
                    IToken enumeratorIdentifier = null;
                    if (LT(1) == IToken.tIDENTIFIER) {
                        enumeratorIdentifier = identifier();
                    } else {
                        IToken la = LA(1);
                        throwBacktrack(la.getOffset(), la.getEndOffset(), la
                                .getLineNumber(), la.getFilename());
                    }
                    Object initialValue = null;
                    if (LT(1) == IToken.tASSIGN) {
                        consume(IToken.tASSIGN);
                        initialValue = constantExpression(sdw.getScope());
                    }
                    Object enumerator = null;
                    if (LT(1) == IToken.tRBRACE) {
                        try {
                            enumerator = null; /*astFactory.addEnumerator(enumeration,
                             enumeratorIdentifier.getCharImage(),
                             enumeratorIdentifier.getOffset(),
                             enumeratorIdentifier.getLineNumber(),
                             enumeratorIdentifier.getOffset(),
                             enumeratorIdentifier.getEndOffset(),
                             enumeratorIdentifier.getLineNumber(), lastToken
                             .getEndOffset(), lastToken
                             .getLineNumber(), initialValue, lastToken.getFilename()); */
                            cleanupLastToken();
                            //					} catch (ASTSemanticException e1) {
                            //						throwBacktrack(e1.getProblem());
                        } catch (Exception e) {
                            int endOffset = (lastToken != null) ? lastToken
                                    .getEndOffset() : 0;
                            logException("enumSpecifier:addEnumerator", e); //$NON-NLS-1$
                            throwBacktrack(mark.getOffset(), endOffset, mark
                                    .getLineNumber(), mark.getFilename());
                        }
                        break;
                    }
                    if (LT(1) != IToken.tCOMMA) {
    //                    enumeration.freeReferences();
    //                  enumerator.freeReferences();
                        int endOffset = (lastToken != null) ? lastToken
                                .getEndOffset() : 0;
                        throwBacktrack(mark.getOffset(), endOffset, mark
                                .getLineNumber(), mark.getFilename());
                    }
                    try {
                        enumerator = null; /*astFactory.addEnumerator(enumeration,
                         enumeratorIdentifier.getCharImage(),
                         enumeratorIdentifier.getOffset(),
                         enumeratorIdentifier.getLineNumber(),
                         enumeratorIdentifier.getOffset(),
                         enumeratorIdentifier.getEndOffset(),
                         enumeratorIdentifier.getLineNumber(), lastToken
                         .getEndOffset(), lastToken.getLineNumber(),
                         initialValue, lastToken.getFilename()); */
                        cleanupLastToken();
                        //				} catch (ASTSemanticException e1) {
                        //					throwBacktrack(e1.getProblem());
                    } catch (Exception e) {
                        int endOffset = (lastToken != null) ? lastToken
                                .getEndOffset() : 0;
                        logException("enumSpecifier:addEnumerator", e); //$NON-NLS-1$
                        throwBacktrack(mark.getOffset(), endOffset, mark
                                .getLineNumber(), mark.getFilename());
                    }
                    consume(IToken.tCOMMA);
                }
                IToken t = consume(IToken.tRBRACE);
    //                enumeration.setEndingOffsetAndLineNumber(t.getEndOffset(), t
    //                        .getLineNumber());
                //			enumeration.acceptElement(requestor);
                sdw.setTypeSpecifier(enumeration);
            } else {
                // enumSpecifierAbort
                int endOffset = (lastToken != null) ? lastToken.getEndOffset() : 0;
                backup(mark);
                throwBacktrack(mark.getOffset(), endOffset, mark.getLineNumber(),
                        mark.getFilename());
            }
        }


}