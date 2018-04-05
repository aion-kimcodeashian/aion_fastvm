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

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.base.util.Bench;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/*
interface Runner0<T> {
    void call(T... arg);
}

interface Runner<T, R> {
    R call(T... arg);
}
*/

public class DummyRepository2 
//extends AionRepositoryImpl
implements IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>>
//implements IRepositoryCache<AccountState, DataWord, AionBlockStore> 
{

    private final AionRepositoryImpl db;
    private final IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo;

    private Map<Address, byte[]> contracts = new HashMap<>();

    public DummyRepository2(AionRepositoryImpl db) {
        this.db = db;
        this.repo = db.startTracking(); // TODO avoid caching
    }

    public void addContract(Address address, byte[] code) {
        contracts.put(address, code);
    }


    @Override
    public AccountState createAccount(Address addr) {
        return Bench.time("createAccount", () -> repo.createAccount(addr));
    }

    @Override
    public boolean hasAccountState(Address addr) {
        return Bench.time("hasAccountState", () -> repo.hasAccountState(addr));
    }

    @Override
    public AccountState getAccountState(Address addr) {
        if (!hasAccountState(addr)) {
            createAccount(addr);
        }
        repo.flush(); // defeat caching
        return Bench.time("getAccountState", () -> repo.getAccountState(addr));
    }

    @Override
    public void deleteAccount(Address addr) {
        Bench.time("deleteAccount", () -> repo.deleteAccount(addr));
    }

    @Override
    public BigInteger incrementNonce(Address addr) {
        // an exception will be thrown if account does not exist
        AccountState as = getAccountState(addr);
        as.incrementNonce();
        return as.getNonce();
    }

    @Override
    public BigInteger setNonce(Address address, BigInteger nonce) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public BigInteger getNonce(Address addr) {
        // an exception will be thrown if account does not exist
        return getAccountState(addr).getNonce();
    }

    @Override
    public IContractDetails<DataWord> getContractDetails(Address addr) {
        repo.flush(); // defeat caching
        return Bench.time("getContractDetails", () -> repo.getContractDetails(addr));
    }

    @Override
    public boolean hasContractDetails(Address addr) {
        repo.flush(); // defeat caching
        return repo.hasContractDetails(addr);
    }

    @Override
    public void saveCode(Address addr, byte[] code) {
        Bench.time("saveCode", () -> repo.saveCode(addr, code));
        contracts.put(addr, code);
    }

    @Override
    public byte[] getCode(Address addr) {
        byte[] code = contracts.get(addr);
        return code == null ? ByteUtil.EMPTY_BYTE_ARRAY : code;
    }

    @Override
    public Map<DataWord, DataWord> getStorage(Address address, Collection<DataWord> keys) {
        repo.flush(); // defeat caching
        return Bench.time("getStorage", () -> repo.getStorage(address, keys));
    }

    @Override
    public int getStorageSize(Address address) {
        repo.flush(); // defeat caching
        return Bench.time("getStorageSize", () -> repo.getStorageSize(address));
    }

    @Override
    public Set<DataWord> getStorageKeys(Address address) {
        repo.flush(); // defeat caching
        return Bench.time("getStorageKeys", () -> repo.getStorageKeys(address));
    }

    @Override
    public void addStorageRow(Address addr, DataWord key, DataWord value) {
        Bench.time("addStorageRow", () -> repo.addStorageRow(addr, key, value));
    }

    @Override
    public DataWord getStorageValue(Address addr, DataWord key) {
        repo.flush(); // defeat caching
        return Bench.time("getStorage", () -> repo.getStorageValue(addr, key));
    }

    @Override
    public BigInteger getBalance(Address addr) {
        return Bench.time("getBalance", () -> getAccountState(addr).getBalance());
    }

    @Override
    public BigInteger addBalance(Address addr, BigInteger value) {
        return Bench.time("addBalance", () -> getAccountState(addr).addToBalance(value));
    }

    //    @Override
    //    public void setBalance(Address addr, BigInteger value) {
    //        getAccountState(addr).setBalance(value);
    //    }
    //
    //    @Override
    //    public Set<Address> getAccountsKeys() {
    //        Set<Address> set = new HashSet<>();
    //        for (Address k : accounts.keySet()) {
    //            set.add(k);
    //        }
    //        return set;
    //    }

    @Override
    public IRepositoryCache
    //<AccountState, DataWord, IBlockStoreBase<?, ?>> 
    startTracking() {
        return this;
    }

    @Override
    public void flush() {
        repo.flush();
    }

    @Override
    public void rollback() {

    }

    @Override
    public void syncToRoot(byte[] root) {

    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isValidRoot(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBatch(Map<Address, AccountState> accountStates,
                            Map<Address, IContractDetails<DataWord>> contractDetailes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadAccountState(Address addr, Map<Address, AccountState> cacheAccounts,
                                 Map<Address, IContractDetails<DataWord>> cacheDetails) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IRepository<AccountState, DataWord, IBlockStoreBase<?, ?>> getSnapshotTo(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AionBlockStore getBlockStore() {
        return db.getBlockStore();
    }

    @Override
    public void compact() {
        throw new UnsupportedOperationException(
                "The tracking cache cannot be compacted. \'Compact\' should be called on the tracked repository.");
    }
}
