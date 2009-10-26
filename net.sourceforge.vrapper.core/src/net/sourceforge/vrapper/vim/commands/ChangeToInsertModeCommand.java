package net.sourceforge.vrapper.vim.commands;

import net.sourceforge.vrapper.vim.EditorAdaptor;
import net.sourceforge.vrapper.vim.modes.ExecuteCommandHint;
import net.sourceforge.vrapper.vim.modes.InsertMode;

public class ChangeToInsertModeCommand extends CountAwareCommand {

    private final Command command;

    public ChangeToInsertModeCommand() {
        this(null);
    }

	public ChangeToInsertModeCommand(Command command) {
        this.command = command;
    }

    @Override
	public void execute(EditorAdaptor editorAdaptor, int count) throws CommandExecutionException {
  		editorAdaptor.changeMode(InsertMode.NAME, new ExecuteCommandHint(command), new InsertMode.WithCountHint(count));
	}

	@Override
	public CountAwareCommand repetition() {
	    return null;
	}
}
