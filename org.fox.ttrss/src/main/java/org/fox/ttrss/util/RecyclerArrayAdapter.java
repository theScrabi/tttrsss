package org.fox.ttrss.util;

import android.support.v7.widget.RecyclerView;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by pascalwelsch on 04.07.14.
 */
public abstract class RecyclerArrayAdapter<T, VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<VH> {

    protected List<T> m_items;

    public RecyclerArrayAdapter(final List<T> objects) {
        m_items = objects;
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     */
    public void add(final T object) {
        m_items.add(object);
        notifyItemInserted(getItemCount() - 1);
    }

    /**
     * Remove all elements from the list.
     */
    public void clear() {
        final int size = getItemCount();
        m_items.clear();
        notifyItemRangeRemoved(0, size);
    }

    @Override
    public int getItemCount() {
        return m_items.size();
    }

    public T getItem(final int position) {
        return m_items.get(position);
    }

    public long getItemId(final int position) {
        return position;
    }

    /**
     * Returns the position of the specified item in the array.
     *
     * @param item The item to retrieve the position of.
     * @return The position of the specified item.
     */
    public int getPosition(final T item) {
        return m_items.indexOf(item);
    }

    /**
     * Inserts the specified object at the specified index in the array.
     *
     * @param object The object to insert into the array.
     * @param index  The index at which the object must be inserted.
     */
    public void insert(final T object, int index) {
        m_items.add(index, object);
        notifyItemInserted(index);

    }

    /**
     * Removes the specified object from the array.
     *
     * @param object The object to remove.
     */
    public void remove(T object) {
        final int position = getPosition(object);
        m_items.remove(object);
        notifyItemRemoved(position);
    }

    /**
     * Sorts the content of this adapter using the specified comparator.
     *
     * @param comparator The comparator used to sort the objects contained in this adapter.
     */
    public void sort(Comparator<? super T> comparator) {
        Collections.sort(m_items, comparator);
        notifyItemRangeChanged(0, getItemCount());
    }
}