package com.github.gclfames5;

import com.github.gclfames5.config.YAMLConfiguration;
import com.github.gclfames5.log.Logger;
import com.github.gclfames5.sw.SplitwiseExpense;
import com.github.gclfames5.sw.SplitwiseHandler;
import com.github.gclfames5.ynab.YNABHandler;
import ynab.client.model.SaveTransaction;
import ynab.client.model.TransactionDetail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {

    public static String LOGFILE_PATH = "logfile.txt";
    public static String configPath = "<UNSPECIFIED>";

    public static void main(String[] args) {

        if (args.length <= 0) {
            System.out.println("Configuration not specified via command line. Defaulting to ./config.yml. To specify a custom path, add path as a command line argument.");
            try {
                File jarDirectory =  new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                configPath = new File(jarDirectory.getParentFile(), "config.yml").getAbsolutePath();
                System.out.println("Configuration path: " + configPath);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                System.err.println("Failed to find working directory! See error for more details.");
                System.exit(0);
            }

        } else {
            System.out.println("Configuration specified via command line: " + args[0]);
            configPath = args[0];
        }

        // Setup the log
        Logger.initLog(LOGFILE_PATH);

        // Read Config
        System.out.println("Reading configuration...");
        File f = new File(configPath);

        YAMLConfiguration config = new YAMLConfiguration(configPath);
        try {
            config.openConfig();
        } catch (FileNotFoundException e) {
            System.out.println("No com.github.gclfames5.config file found!");
            e.printStackTrace();
        }

        // Authenticate Splitwise
        Logger.log("Authenticating Splitwise...", true);
        SplitwiseHandler sw = new SplitwiseHandler(config);
        sw.authenticate(true);
        Date last_update = config.getSplitwiseLastTransactionDate();
        Logger.log(String.format("Fetching spltiwise transactions since %s", last_update.toString()), true);
        List<SplitwiseExpense> expensesToProcess = sw.getAllExpenses(0, last_update);

        // Authenticate YNAB
        Logger.log("Authenticating YNAB...", true);
        YNABHandler ynab = new YNABHandler(config);
        ynab.authenticate();

        // Search through all updated Spltiwise Transactions
        // If any have been updated (or deleted) determine the earliest
        // "created_on" date to determine how far back we have to search in
        // YNAB in order to fund the corresponding transaction

        List<SplitwiseExpense> newSplitwiseExpenses = new ArrayList<>();
        List<SplitwiseExpense> updatedSplitwiseExpenses = new ArrayList<>();
        List<SplitwiseExpense> deletedSplitwiseExpenses = new ArrayList<>();
        Date oldestCreationDate = new Date();

        // Sort Expenses into "new" and "updated" categories while also finding oldest "created_at" date
        for (SplitwiseExpense expense : expensesToProcess) {
            expense.description += String.format(", sw_uuid:%d", expense.id);
            if (expense.created_at.before(oldestCreationDate))
                oldestCreationDate = expense.created_at;

            // If deleted, add to deleted expenses
            if (expense.deleted_at != null) {
              if (expense.created_at.compareTo(last_update) > 0) {
                continue;
              }
              deletedSplitwiseExpenses.add(expense);
            }
            else if (expense.created_at.compareTo(last_update) > 0) {
                newSplitwiseExpenses.add(expense);
            } else {
                updatedSplitwiseExpenses.add(expense);
            }
        }

        // Add all new transactions to YNAB
        Logger.log(String.format("Number of new expenses found: %d", newSplitwiseExpenses.size()), true);
        for (SplitwiseExpense newExpense : newSplitwiseExpenses) {
            Logger.log(String.format("New transaction found: %s", newExpense.toString()), true);
            Logger.log(String.format("Uploading new expense to YNAB: %s", newExpense.toString()), true);
            ynab.addTransaction(newExpense.cost, newExpense.description, newExpense.created_at);
        }

        // For all transactions that seem to be updated:
        //  - Try to match them with a YNAB expense and perform update
        //  - Otherwise, treat as a new transaction
        Logger.log(String.format("Number of modified expenses found: %d", updatedSplitwiseExpenses.size()), true);
        if (updatedSplitwiseExpenses.size() > 0) {
            // // Get the minimum number of transactions from YNAB to search through UUIDs
            // List<TransactionDetail> ynabTransactions = ynab.getTransactionsSince(new Date(0));
            // splitwiseLoop:
            for (SplitwiseExpense updatedExpense : updatedSplitwiseExpenses) {
                Logger.log(String.format("Modified expense: %s", updatedExpense.toString()), true);
                // // Search for corresponding expense in YNAB
                // ynabLoop:
                // for (TransactionDetail ynabTransaction : ynabTransactions) {
                //     // Ignore any transactions without memos, these can't have been
                //     // generated by us anyway
                //     if (ynabTransaction == null || ynabTransaction.getMemo() == null) {
                //         Logger.log(String.format("Saw null transaction or null memo while searching for updated transactions"));
                //         continue ynabLoop;
                //     }
>>>>>>> 63a0e69 (fix tz issue and add logging, update new expense logic)

                //     String[] search = ynabTransaction.getMemo().split("sw_uuid:");

                //     Logger.log(String.format("Searching transaction: %s with memo: %s", ynabTransaction.toString(), ynabTransaction.getMemo()));
                //     // Transactions may exist that have not been tagged with the uuid
                //     // ignore those entries
                //     if (search.length > 1) {
                //         int uuid = Integer.valueOf(search[1]);
                //         // Check if this uuid matches the splitwise expense we're looking at
                //         // If it matches, update this transaction
                //         if (uuid == updatedExpense.id) {
                //             Logger.log(String.format("Matched updated transaction: %s with uuid %s",
                //                     updatedExpense.toString(), ynabTransaction.getAccountId().toString()), true);
                //             SaveTransaction saveTransaction = ynab.buildTransaction(updatedExpense.cost, updatedExpense.description, updatedExpense.created_at);
                //             // Don't bother buffering, can't bulk update
                //             Logger.log(String.format("Uploading updated expense to YNAB: %s", updatedExpense.toString()), true);
                //             ynab.updateTransaction(ynabTransaction.getId(), saveTransaction);
                //             continue splitwiseLoop;
                //         }
                //     }
                // }
            }
        }

        Logger.log(String.format("Number of deleted expenses found: %d", deletedSplitwiseExpenses.size()), true);
        if (deletedSplitwiseExpenses.size() > 0) {
            // Get the minimum number of transactions from YNAB to search through UUIDs
            // List<TransactionDetail> ynabTransactions = ynab.getTransactionsSince(new Date(0));
            splitwiseLoop:
            for (SplitwiseExpense deletedExpense : deletedSplitwiseExpenses) {
                Logger.log(String.format("Deleted expense: %s", deletedExpense.toString()), true);
                // // Search for corresponding expense in YNAB
                // ynabLoop:
                // for (TransactionDetail ynabTransaction : ynabTransactions) {
                //     // Ignore any transactions without memos, these can't have been
                //     // generated by us anyway
                //     if (ynabTransaction == null || ynabTransaction.getMemo() == null) {
                //         Logger.log(String.format("Saw null transaction or null memo while searching for deleted transactions"));
                //         continue ynabLoop;
                //     }

                //     String[] search = ynabTransaction.getMemo().split("sw_uuid:");

                //     Logger.log(String.format("Searching transaction: %s with memo: %s", ynabTransaction.toString(), ynabTransaction.getMemo()));
                //     // Transactions may exist that have not been tagged with the uuid
                //     // ignore those entries
                //     if (search.length > 1) {
                //         int uuid = Integer.valueOf(search[1]);
                //         // Check if this uuid matches the splitwise expense we're looking at
                //         // If it matches, update this transaction
                //         if (uuid == deletedExpense.id) {
                //             Logger.log(String.format("Matched deleted transaction: %s with uuid %s",
                //                     deletedExpense.toString(), ynabTransaction.getAccountId().toString()), true);
                //             SaveTransaction saveTransaction = ynab.buildTransaction(0, deletedExpense.description, deletedExpense.created_at);
                //             // Don't bother buffering, can't bulk update
                //             Logger.log(String.format("Uploading deleted expense to YNAB: %s", deletedExpense.toString()), true);
                //             ynab.updateTransaction(ynabTransaction.getId(), saveTransaction);
                //             continue splitwiseLoop;
                //         }
                //     }
                // }
            }
        }

        config.setSplitwiseLastTransactionDate(new Date()); // TODO: potential for issues here

        Logger.log("Writing config...", true);
        try {
            config.writeConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Exiting...");
        System.exit(0);
    }

}
