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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coffee producer/consumer sample using a bounded buffer as bar top.
 */
public class BoundedBufferMain {

    private static final int KAHVECIS = 2;
    private static final int COFFEE_ADDICTS = 2;

    public static final int BARTOP_CAPACITY = 100000;

    static final AtomicInteger produced = new AtomicInteger();
    static final AtomicInteger consumed = new AtomicInteger();

    static class Coffee {
        private String size;
        private int sugar;
        private String type;

        public Coffee(String size, int sugar, String type) {
            this.size = size;
            this.sugar = sugar;
            this.type = type;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public int getSugar() {
            return sugar;
        }

        public void setSugar(int sugar) {
            this.sugar = sugar;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "Coffee{" +
                    "size='" + size + '\'' +
                    ", sugar=" + sugar +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    static class Kahveci implements Runnable {

        private final BoundedBuffer<Coffee> coffeeOnBar;
        final String[] sizes = new String[] {"ristretto", "espresso", "lungo"};
        final String[] types = new String[] {"kahve", "espresso", "american"};

        volatile boolean brewCoffee = true;
        final Random random = new Random();


        public Kahveci(BoundedBuffer<Coffee> coffeeOnBar) {
            this.coffeeOnBar = coffeeOnBar;
        }

        @Override
        public void run() {
            System.out.println("Starting kahveci");
            while (brewCoffee) {
                Coffee c = new Coffee(randomSize(), random.nextInt(3), randomType());
                coffeeOnBar.put(c);
                produced.incrementAndGet();
            }
            System.out.println("Stopping kahveci");
        }

        private String randomSize() {
            return sizes[random.nextInt(3)];
        }

        private String randomType() {
            return types[random.nextInt(3)];
        }
    }

    static class CoffeeAddict implements Runnable {
        private final BoundedBuffer<Coffee> coffeeOnBar;

        volatile boolean drinkCoffee = true;

        public CoffeeAddict(BoundedBuffer<Coffee> coffeeOnBar) {
            this.coffeeOnBar = coffeeOnBar;
        }

        @Override
        public void run() {
            System.out.println("Starting coffee addict");
            while (drinkCoffee) {
                Coffee c = coffeeOnBar.take();
                consumed.incrementAndGet();
            }
            System.out.println("Stopping coffee addict");
        }
    }

    public static void main(String[] args)
            throws InterruptedException {

        HazelcastInstance hz = Hazelcast.newHazelcastInstance();

        BoundedBuffer<Coffee> coffeeBuffer = new BoundedBuffer<Coffee>(hz, BARTOP_CAPACITY);

        ExecutorService executorService = Executors.newFixedThreadPool(KAHVECIS + COFFEE_ADDICTS);

        Kahveci[] kahvecis = new Kahveci[KAHVECIS];
        for (int i=0; i < KAHVECIS; i++) {
            kahvecis[i] = new Kahveci(coffeeBuffer);
            executorService.submit(kahvecis[i]);
        }

        CoffeeAddict[] coffeeAddicts = new CoffeeAddict[COFFEE_ADDICTS];
        for (int i=0; i < COFFEE_ADDICTS; i++) {
            coffeeAddicts[i] = new CoffeeAddict(coffeeBuffer);
            executorService.submit(coffeeAddicts[i]);
        }

        // allow this to run then stop them all
        Thread.sleep(3 * 60 * 1000);

        for (Kahveci k : kahvecis) {
            k.brewCoffee = false;
        }

        // allow all coffee to be consumed
        while (!coffeeBuffer.isEmpty()) {
            Thread.sleep(50);
        }

        for (CoffeeAddict coffeeAddict : coffeeAddicts) {
            coffeeAddict.drinkCoffee = false;
        }

        executorService.shutdown();

        hz.shutdown();

        if (produced.get() != consumed.get()) {
            System.out.println("Ouch -- produced " + produced.get() + " coffees and consumed " + consumed.get());
        }
        else {
            System.out.println("Brewed and drank " + produced.get() + " coffees");
        }
    }
}
