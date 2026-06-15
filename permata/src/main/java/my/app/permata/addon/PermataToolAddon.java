package my.app.permata.addon;

import my.app.permata.ui.fragment.ToolBarMediator;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.view.ToolBarView;

/**
 * @author sklchan77
 */
public interface PermataToolAddon extends PermataActivityAddon {
	void contributeTool(ToolBarMediator m, ToolBarView tb, ActivityFragment f);
}
