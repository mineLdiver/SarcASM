package net.mine_diver.sarcasm.util.collection;

import net.mine_diver.sarcasm.util.Util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * idk
 *
 * @param <T> the element type
 */
public final class ConditionalSet<T> implements Iterable<T> {

    private final Set<Entry> entries = Util.newIdentitySet();

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public EntryBuilder entry(T element) {
        return new EntryBuilder(element);
    }

    public final class EntryBuilder {
        private final T value;
        private final Set<T> after = Util.newIdentitySet();
        private final Set<T> before = Util.newIdentitySet();

        private EntryBuilder(T value) {
            this.value = value;
        }

        @SafeVarargs
        public final EntryBuilder after(T... after) {
            this.after.addAll(Arrays.asList(after));
            return this;
        }

        @SafeVarargs
        public final EntryBuilder before(T... before) {
            this.before.addAll(Arrays.asList(before));
            return this;
        }

        public void add() {
            entries.stream().filter(entry -> entry.before.contains(value)).map(entry -> entry.value).forEach(after::add);
            entries.stream().filter(entry -> before.contains(entry.value)).forEach(entry -> entry.after.add(value));
            entries.add(new Entry(after, value, Collections.unmodifiableSet(before)));
        }
    }

    private final class Entry {
        private final Set<T> after;
        private final T value;
        private final Set<T> before;

        private Entry(Set<T> after, T value, Set<T> before) {
            this.after = after;
            this.value = value;
            this.before = before;
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private final Deque<T> currentQueue = new LinkedList<>();
            class IterableEntry {
                private final Entry entry;
                private boolean unmatched = true;
                private final Set<T> met = Util.newIdentitySet();
                private int score;

                private IterableEntry(Entry entry) {
                    this.entry = entry;
                    score = entry.after.size();
                    accept(null);
                }

                private void accept(T currentValue) {
                    if (unmatched) {
                        if (entry.after.contains(currentValue) && met.add(currentValue))
                            score--;
                        if (entry.before.contains(currentValue))
                            throw new IllegalStateException("This should never be possible");
                        if (score == 0) {
                            currentQueue.offerLast(entry.value);
                            unmatched = false;
                        }
                    }
                }
            }
            private final Set<IterableEntry> entries = ConditionalSet.this.entries.stream().map(IterableEntry::new).collect(Collectors.toCollection(Util::newIdentitySet));

            @Override
            public boolean hasNext() {
                return !currentQueue.isEmpty();
            }

            @Override
            public T next() {
                T next = currentQueue.pollFirst();
                entries.forEach(entry -> entry.accept(next));
                return next;
            }
        };
    }
}
