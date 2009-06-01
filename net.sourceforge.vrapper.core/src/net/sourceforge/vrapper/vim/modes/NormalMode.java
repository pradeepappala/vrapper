package net.sourceforge.vrapper.vim.modes;

import static java.lang.Math.min;
import static net.sourceforge.vrapper.keymap.StateUtils.union;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.changeCaret;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.convertKeyStroke;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.leafBind;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.leafCtrlBind;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.operatorCmds;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.operatorCmdsWithUpperCase;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.prefixedOperatorCmds;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.state;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.transitionBind;
import static net.sourceforge.vrapper.keymap.vim.GoThereState.motion2command;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.cmd;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.dontRepeat;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.editText;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.go;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.javaEditText;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.seq;
import net.sourceforge.vrapper.keymap.State;
import net.sourceforge.vrapper.keymap.vim.CountingState;
import net.sourceforge.vrapper.keymap.vim.GoThereState;
import net.sourceforge.vrapper.keymap.vim.TextObjectState;
import net.sourceforge.vrapper.platform.TextContent;
import net.sourceforge.vrapper.utils.CaretType;
import net.sourceforge.vrapper.vim.EditorAdaptor;
import net.sourceforge.vrapper.vim.Options;
import net.sourceforge.vrapper.vim.VimConstants;
import net.sourceforge.vrapper.vim.commands.BorderPolicy;
import net.sourceforge.vrapper.vim.commands.ChangeModeCommand;
import net.sourceforge.vrapper.vim.commands.ChangeOperation;
import net.sourceforge.vrapper.vim.commands.Command;
import net.sourceforge.vrapper.vim.commands.CountIgnoringNonRepeatableCommand;
import net.sourceforge.vrapper.vim.commands.DeleteOperation;
import net.sourceforge.vrapper.vim.commands.DotCommand;
import net.sourceforge.vrapper.vim.commands.LinewiseVisualMotionCommand;
import net.sourceforge.vrapper.vim.commands.MotionPairTextObject;
import net.sourceforge.vrapper.vim.commands.MotionTextObject;
import net.sourceforge.vrapper.vim.commands.OptionDependentTextObject;
import net.sourceforge.vrapper.vim.commands.PasteAfterCommand;
import net.sourceforge.vrapper.vim.commands.PasteBeforeCommand;
import net.sourceforge.vrapper.vim.commands.RedoCommand;
import net.sourceforge.vrapper.vim.commands.ReplaceCommand;
import net.sourceforge.vrapper.vim.commands.StickToEOLCommand;
import net.sourceforge.vrapper.vim.commands.SwapCaseCommand;
import net.sourceforge.vrapper.vim.commands.TextObject;
import net.sourceforge.vrapper.vim.commands.TextOperation;
import net.sourceforge.vrapper.vim.commands.TextOperationTextObjectCommand;
import net.sourceforge.vrapper.vim.commands.UndoCommand;
import net.sourceforge.vrapper.vim.commands.VisualMotionCommand;
import net.sourceforge.vrapper.vim.commands.YankOperation;
import net.sourceforge.vrapper.vim.commands.motions.LineEndMotion;
import net.sourceforge.vrapper.vim.commands.motions.LineStartMotion;
import net.sourceforge.vrapper.vim.commands.motions.Motion;
import net.sourceforge.vrapper.vim.commands.motions.MoveLeft;
import net.sourceforge.vrapper.vim.commands.motions.MoveRight;
import net.sourceforge.vrapper.vim.commands.motions.MoveWordEndRight;
import net.sourceforge.vrapper.vim.commands.motions.MoveWordLeft;
import net.sourceforge.vrapper.vim.commands.motions.MoveWordRight;

public class NormalMode extends CommandBasedMode {

    public static final String KEYMAP_NAME = "Normal Mode Keymap";
    public static final String NAME = "normal mode";


    public NormalMode(EditorAdaptor editorAdaptor) {
        super(editorAdaptor);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected KeyMapResolver buildKeyMapResolver() {
        State<String> state = union(
                state(
                    leafBind('r', KeyMapResolver.NO_KEYMAP)),
                getKeyMapsForMotions());
        State<String> countEater = new CountConsumingState(state);
        return new KeyMapResolver(countEater, KEYMAP_NAME);
    }

    @Override
    protected State<Command> getInitialState() {
        Command visualMode = new ChangeModeCommand(VisualMode.NAME);
        Command linewiseVisualMode = new ChangeModeCommand(LinewiseVisualMode.NAME);

        Command deselectAll = new CountIgnoringNonRepeatableCommand() {
            public void execute(EditorAdaptor editorMode) {
                editorAdaptor.setPosition(editorAdaptor.getSelection().getEnd(), true);
            }
        };

        final Motion moveLeft = new MoveLeft();
        final Motion moveRight = new MoveRight();
        final Motion wordRight = new MoveWordRight();
        final Motion wordLeft = new MoveWordLeft();
        final Motion wordEndRight = new MoveWordEndRight();
        final Motion bol = new LineStartMotion(true);
        final Motion eol = new LineEndMotion(BorderPolicy.EXCLUSIVE);

        final State<Motion> motions = motions();
        final TextObject innerWord = new MotionPairTextObject(wordLeft, wordEndRight);
        final TextObject aWord = new MotionPairTextObject(wordLeft, wordRight);
        final TextObject wordForCW = new OptionDependentTextObject(Options.STUPID_CW, wordEndRight, wordRight);
        final TextObject toEol = new MotionTextObject(eol);
        final TextObject wholeLine = new MotionTextObject(new LineEndMotion(BorderPolicy.LINE_WISE));
        final TextObject toEolForY = new OptionDependentTextObject(Options.STUPID_Y, wholeLine, toEol);

        @SuppressWarnings("unchecked")
        State<TextObject> textObjects = union(
                state(
                        transitionBind('i',
                                leafBind('w', innerWord)),
                                transitionBind('a',
                                        leafBind('w', aWord))),
                                        new TextObjectState(motions));

        @SuppressWarnings("unchecked")
        State<TextObject> textObjectsForChange = CountingState.wrap(union(state(leafBind('w', wordForCW)), textObjects));

        textObjects = CountingState.wrap(textObjects);

        TextOperation delete = new DeleteOperation();
        TextOperation change = new ChangeOperation();
        TextOperation yank   = new YankOperation();
        Command undo = new UndoCommand();
        Command redo = new RedoCommand();
        Command pasteAfter  = new PasteAfterCommand();
        Command pasteBefore = new PasteBeforeCommand();
        Command deleteNext = new TextOperationTextObjectCommand(delete, new MotionTextObject(moveRight));
        Command deletePrevious = seq(motion2command(moveLeft), deleteNext); // FIXME: should do nothing when on first character of buffer
        Command repeatLastOne = new DotCommand();
        Command tildeCmd = new SwapCaseCommand();
        Command stickToEOL = new StickToEOLCommand();

        State<Command> motionCommands = new GoThereState(motions);

        @SuppressWarnings("unchecked")
        State<Command> commands = CountingState.wrap(union(
                operatorCmdsWithUpperCase('d', delete, toEol,     textObjects),
                operatorCmdsWithUpperCase('y', yank,   toEolForY, textObjects),
                operatorCmdsWithUpperCase('c', change, toEol,     textObjectsForChange),
                operatorCmds('=', seq(javaEditText("indent"), deselectAll), textObjects),
                prefixedOperatorCmds('g', 'c', seq(javaEditText("toggle.comment"), deselectAll), textObjects),
                prefixedOperatorCmds('g', 'u', seq(editText("lowerCase"), deselectAll), textObjects),
                prefixedOperatorCmds('g', 'U', seq(editText("upperCase"), deselectAll), textObjects),
                state(leafBind('$', stickToEOL)),
                motionCommands,
                state(
                        leafBind('i', (Command) new ChangeModeCommand(InsertMode.NAME)),
                        leafBind('a', (Command) new ChangeModeCommand(InsertMode.NAME, moveRight)),
                        leafBind('I', (Command) new ChangeModeCommand(InsertMode.NAME, bol)),
                        leafBind('A', (Command) new ChangeModeCommand(InsertMode.NAME, eol)),
                        leafBind(':', (Command) new ChangeModeCommand(CommandLineMode.NAME)),
                        leafBind('?', (Command) new ChangeModeCommand(SearchMode.NAME, SearchMode.Direction.BACKWARD)),
                        leafBind('/', (Command) new ChangeModeCommand(SearchMode.NAME, SearchMode.Direction.FORWARD)),
                        leafBind('R', (Command) new ChangeModeCommand(ReplaceMode.NAME)),
                        leafBind('o', seq(new ChangeModeCommand(InsertMode.NAME), editText("smartEnter"))), // FIXME: use Vrapper's code; repetition
                        leafBind('O', seq(new ChangeModeCommand(InsertMode.NAME), editText("smartEnterInverse"))), // FIXME: use Vrapper's code; repetition
                        leafBind('v', seq(visualMode, new VisualMotionCommand(moveRight))),
                        leafBind('V', seq(linewiseVisualMode, new LinewiseVisualMotionCommand(moveRight))),
                        leafBind('p', pasteAfter),
                        leafBind('.', repeatLastOne),
                        leafBind('P', pasteBefore),
                        leafBind('J', (Command) editText("join.lines")),
                        leafBind('x', deleteNext),
                        leafBind('X', deletePrevious),
                        leafBind('~', tildeCmd),
                        leafBind('s', seq(deleteNext, new ChangeModeCommand(InsertMode.NAME))), // FIXME: this should be compound edit
                        transitionBind('r', changeCaret(CaretType.UNDERLINE),
                                convertKeyStroke(
                                        ReplaceCommand.KEYSTROKE_CONVERTER,
                                        VimConstants.PRINTABLE_KEYSTROKES)),
                        transitionBind('z',
                                leafBind('o', dontRepeat(editText("folding.expand"))),
                                leafBind('R', dontRepeat(editText("folding.expand_all"))),
                                leafBind('c', dontRepeat(editText("folding.collapse"))),
                                leafBind('M', dontRepeat(editText("folding.collapse_all")))),
                        transitionBind('g',
                                leafBind('r', javaEditText("refactor.quickMenu")),
                                leafBind('R', javaEditText("rename.element")),
                                leafBind('t', cmd("org.eclipse.ui.window.nextEditor")),
                                leafBind('T', cmd("org.eclipse.ui.window.previousEditor"))),
                        leafBind('u', undo),
                        leafCtrlBind('r', redo),
                        leafCtrlBind('b', go("goto.pageUp")),
                        leafCtrlBind('f', go("goto.pageDown")),
                        leafCtrlBind('y', dontRepeat(editText("scroll.lineUp"))),
                        leafCtrlBind('e', dontRepeat(editText("scroll.lineDown"))),
                        leafCtrlBind(']', seq(javaEditText("open.editor"), deselectAll)), // NOTE: deselect won't work in other editor
                        leafCtrlBind('i', dontRepeat(cmd("org.eclipse.ui.navigate.forwardHistory"))),
                        leafCtrlBind('o', dontRepeat(cmd("org.eclipse.ui.navigate.backwardHistory"))))));

        return commands;
    }

    @Override
    protected void placeCursor() {
        TextContent content = editorAdaptor.getViewContent();
        int checkFrom = editorAdaptor.getPosition().getViewOffset() - 1;
        int checkTo   = min(checkFrom + 2, content.getTextLength());
        if (isEnabled && checkFrom >= 0) {
            String around = content.getText(checkFrom, checkTo - checkFrom);
            if (!around.startsWith("\n") && (around.endsWith("\n") || around.length() == 1)) {
                editorAdaptor.setPosition(editorAdaptor.getPosition().addViewOffset(-1), false);
            }
        }
    }

    @Override
    protected void commandDone() {
        super.commandDone();
        editorAdaptor.getCursorService().setCaret(CaretType.RECTANGULAR);
    }

    public void enterMode(Object... args) {
        if (isEnabled) {
            return;
        }
        isEnabled = true;
        placeCursor();
        editorAdaptor.getCursorService().setCaret(CaretType.RECTANGULAR);
    }

    public void leaveMode() {
        if (!isEnabled) {
            return;
        }
        isEnabled = false;
    }

    public String getName() {
        return NAME;
    }
}