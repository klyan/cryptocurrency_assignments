import java.security.PublicKey;
import java.util.*;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        UTXOPool uniqueUtxos = new UTXOPool();
        double previousTxOutSum = 0;
        double currentTxOutSum = 0;
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input in = tx.getInput(i);
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if (!utxoPool.contains(utxo))          // all previous outputs claimed by {@code tx} are in the current UTXO pool,
                return false;
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            if(null == output || null == in.signature){
                return false;
            }
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), in.signature))
                return false;
            if (uniqueUtxos.contains(utxo))    //double-spent
                return false;
            uniqueUtxos.addUTXO(utxo, output);
            previousTxOutSum += output.value;
        }
        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0)    //non-negative
                return false;
            currentTxOutSum += out.value;
        }
        return previousTxOutSum >= currentTxOutSum;   // input>=output
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Set<Transaction> validTxs = new HashSet<Transaction>();
        if(null == possibleTxs || possibleTxs.length < 1){
            return possibleTxs;
        }
        for(Transaction tx: possibleTxs){
            if(isValidTx(tx)){
                ArrayList<Transaction.Input> inputs = tx.getInputs();
                for(Transaction.Input input : inputs){
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    utxoPool.removeUTXO(utxo);

                }
                ArrayList<Transaction.Output> outputs = tx.getOutputs();
                for(int i = 0; i < tx.numOutputs(); i++){
                    Transaction.Output output = tx.getOutput(i);
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    utxoPool.addUTXO(utxo, output);
                }
                validTxs.add(tx);
            }
        }
        return validTxs.toArray(new Transaction[validTxs.size()]);
    }

}
