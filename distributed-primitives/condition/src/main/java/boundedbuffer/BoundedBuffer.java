/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boundedbuffer;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICondition;
import com.hazelcast.core.ILock;

import java.util.UUID;

/**
 * A sample bounded buffer backed by Hazelcast-based distributed versions of
 * {@link java.util.concurrent.locks.Lock} and {@link java.util.concurrent.locks.Condition},
 * largely as seen on JCiP, chapter 14.
 */
public class BoundedBuffer<V> {

    // this lock guards this class' internal state {buffer, head, tail, count}
    private final ILock lock;
    // condition predicates
    private final ICondition isEmptyCondition, isFullCondition;

    private final V[] buffer;
    private int head, tail, count;

    public BoundedBuffer(HazelcastInstance hz, int capacity) {
        this.buffer = (V[]) new Object[capacity];
        // a unique lock per bounded buffer instance
        lock = hz.getLock("BoundedBuffer-" + UUID.randomUUID().toString());
        isEmptyCondition = lock.newCondition("isEmpty");
        isFullCondition = lock.newCondition("isFull");
    }

    /**
     * May block until there is room in the buffer to put the new value.
     * @param v
     */
    public void put(V v) {
        try {
            lock.lock();
            while (isFull()) {
                isFullCondition.awaitUninterruptibly();
            }
            doPut(v);
            isEmptyCondition.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * May block until there is a value to take from the buffer.
     * @return
     */
    public V take() {
        try {
            lock.lock();
            while (isEmpty()) {
                isEmptyCondition.awaitUninterruptibly();
            }
            V v = doTake();
            isFullCondition.signalAll();
            return v;
        }
        finally {
            lock.unlock();
        }
    }

    public void doPut(V v) {
        try {
            lock.lock();
            buffer[tail] = v;
            if (++tail == buffer.length) {
                tail = 0;
            }
            count++;
        }
        finally {
            lock.unlock();
        }
    }

    public V doTake() {
        try {
            lock.lock();
            V v = buffer[head];
            buffer[head] = null;
            if (++head == buffer.length) {
                head = 0;
            }
            count--;
            return v;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        try {
            lock.lock();
            return count == 0;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isFull() {
        try {
            lock.lock();
            return count == buffer.length;
        }
        finally {
            lock.unlock();
        }
    }

}
