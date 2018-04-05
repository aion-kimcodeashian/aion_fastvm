/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.fastvm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.Hex;
import org.aion.fastvm.Instruction.*;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.ExecutionResult.Code;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.vm.TransactionResult;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayOutputStream;

import static org.aion.fastvm.Instruction.*;
import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NrgCostTest {
    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = Address.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWord blockDifficulty = new DataWord(0x100000000L);

    private DataWord nrgPrice;
    private long nrgLimit;
    private DataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private TransactionResult txResult;

    private final DummyRepository2 repo;
    

    public NrgCostTest() {
        AionRepositoryImpl db = AionRepositoryImpl.inst();
        repo = new DummyRepository2(db);
    }

    @BeforeClass
    public static void note() {
        System.out.println("\nNOTE: compilation time was not counted; extra cpu time was introduced for some opcodes.");

    }

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 10_000_000L;
        callValue = DataWord.ZERO;
        callData = new byte[0];
        txResult = new TransactionResult();

        // JVM warm up
        byte[] code = {0x00};
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        DummyRepository2 repo = new DummyRepository2(AionRepositoryImpl.inst());
        repo.addContract(address, code);
        for (int i = 0; i < 10000; i++) {
            new FastVM().run(code, ctx, repo);
        }
    }

    private byte[] repeat(int n, Object... codes) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            for (Object o : codes) {
                buf.write(o instanceof Instruction ? ((Instruction) o).code() : ((Integer) o).byteValue());
            }
        }

        return buf.toByteArray();
    }

    @Test
    public void test1Base() {
        /**
         * Number of repeats of the instruction. You may get different results
         * by adjusting this number. It's a tradeoff between the instruction
         * execution and system cost. We should only interpret the results
         * relatively.
         */
        int x = 64;

        /**
         * Number of VM invoking.
         */
        int y = 1000;

        /**
         * Energy cost for this group of instructions.
         */
        int z = Tier.BASE.cost(); // energy cost

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of the Base tier");
        System.out.println("========================================================================");

        Instruction[] instructions = {ADDRESS, ORIGIN, CALLER, CALLVALUE, CALLDATASIZE, CODESIZE, GASPRICE, COINBASE,
                TIMESTAMP, NUMBER, DIFFICULTY, GASLIMIT, /* POP, */ PC, MSIZE, GAS};

        for (Instruction inst : instructions) {
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());
            
            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }

        System.out.println();
    }

    @Test
    public void test2VeryLow() {
        int x = 64;
        int y = 1000;
        int z = Tier.VERY_LOW.cost();

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println("========================================================================");

        Instruction[] instructions = {ADD, SUB, NOT, LT, GT, SLT, SGT, EQ, ISZERO, AND, OR, XOR, BYTE, CALLDATALOAD,
                MLOAD, MSTORE, MSTORE8, /* PUSH1, */ DUP1, SWAP1};

        for (Instruction inst : instructions) {
            callData = Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test3Low() {
        int x = 64;
        int y = 1000;
        int z = Tier.LOW.cost();

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of the Low tier");
        System.out.println("========================================================================");

        Instruction[] instructions = {MUL, DIV, SDIV, MOD, SMOD, SIGNEXTEND};

        for (Instruction inst : instructions) {
            callData = Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test4Mid() {
        int x = 64;
        int y = 1000;
        int z = Tier.MID.cost();

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of the Mid tier");
        System.out.println("========================================================================");

        Instruction[] instructions = {ADDMOD, MULMOD, /* JUMP */};

        for (Instruction inst : instructions) {
            callData = Hex.decode("000000000000000000000000000000010000000000000000000000000000000200000000000000000000000000000003");
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test5High() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println("========================================================================");

        Instruction[] instructions = {JUMPI};

        for (Instruction inst : instructions) {
            callData = Hex.decode("000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000003");
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test6SHA3() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println("========================================================================");

        Instruction[] instructions = {SHA3};

        for (Instruction inst : instructions) {
            callData = Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code = repeat(x, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst, POP, POP);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            System.out.println(result);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }


    @Test
    public void test7Exp() {
        int x = 64;
        int y = 1000;
        int z = 10;

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of EXP");
        System.out.println("========================================================================");

        Instruction[] instructions = {EXP};

        for (Instruction inst : instructions) {
            callData = Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            DummyRepository repo = new DummyRepository();
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void testSStore() {
        int x = 64;
        int y = 1000;
        int z = 10;

        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of SSTORE, SLOAD");
        System.out.println("========================================================================");

        Instruction[] instructions = {SSTORE, SLOAD};

        for (Instruction inst : instructions) {
            callData = Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

            ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                    callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                    blockDifficulty, txResult);
            repo.addContract(address, code);

            // compile
            ExecutionResult result = new FastVM().run(code, ctx, repo);
            assertEquals(Code.SUCCESS, result.getCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().run(code, ctx, repo);
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                    (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }
    
    @Test
    public void test8Remaining() {
        int x = 64;
        int y = 1000;
        int z = 10;
        System.out.println("\n========================================================================");
        System.out.println("Cost for instructions of OTHER");
        System.out.println("========================================================================");

        for (Instruction inst : Instruction.values()) {
            if (inst.tier() != Tier.BASE && inst.tier() != Tier.LOW && inst.tier() != Tier.VERY_LOW
                    && inst.tier() != Tier.MID && inst.tier() != Tier.HIGH) {
                callData = Hex.decode("0000000000000000000000000000000100000000000000000000000000000002");
                byte[] code = repeat(x, PUSH1, 32, CALLDATALOAD, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst);

                ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                        callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                        blockDifficulty, txResult);
                //DummyRepository2 drepo = new DummyRepository2();
                repo.addContract(address, code);

                // compile
                ExecutionResult result = new FastVM().run(code, ctx, repo);
                if (Code.SUCCESS != result.getCode()) {
                    System.out.printf("%12s: %s\n", inst.name(), result.getCode());
                    continue;
                }
                //assertEquals(Code.SUCCESS, result.getCode());

                long t1 = System.nanoTime();
                for (int i = 0; i < y; i++) {
                    new FastVM().run(code, ctx, repo);
                }
                long t2 = System.nanoTime();

                long c = (t2 - t1) / y / x;
                System.out.printf("%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n", inst.name(), c,
                        (nrgLimit / z) * c / 1_000_000, nrgLimit);
            }
        }
    }
}
