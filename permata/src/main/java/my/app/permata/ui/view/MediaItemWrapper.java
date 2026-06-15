package my.app.permata.ui.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.lib.MediaLib.PlayableItem;

/**
 * @author sklchan77
 */
public class MediaItemWrapper {
	private final Item item;
	private boolean selected;
	@Nullable
	private MediaItemViewHolder viewHolder;

	public MediaItemWrapper(Item item) {
		this.item = item;
	}

	public Item getItem() {
		return item;
	}

	public void setViewHolder(@Nullable MediaItemViewHolder h) {
		viewHolder = h;
		if (h != null) h.getItemView().getCheckBox().setSelected(isSelected());
	}

	@Nullable
	public MediaItemViewHolder getViewHolder() {
		return viewHolder;
	}

	@Nullable
	public MediaItemView getView() {
		MediaItemViewHolder h = getViewHolder();
		return (h != null) ? h.getItemView() : null;
	}

	public void refreshViewCheckbox() {
		MediaItemView v = getView();
		if (v != null) v.refreshCheckbox();
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected, boolean refreshViewCheckbox) {
		if (isSelectionSupported()) {
			this.selected = selected;
			if (refreshViewCheckbox) refreshViewCheckbox();
		}
	}

	public boolean isSelectionSupported() {
		return (getItem() instanceof PlayableItem);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof MediaItemWrapper)) return false;
		return getItem().equals(((MediaItemWrapper) o).getItem());
	}

	@Override
	public int hashCode() {
		return Objects.hash(item);
	}

	@NonNull
	@Override
	public String toString() {
		return getItem().toString();
	}
}
