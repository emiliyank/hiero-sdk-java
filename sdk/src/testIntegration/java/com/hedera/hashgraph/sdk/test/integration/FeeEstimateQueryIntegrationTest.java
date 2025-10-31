// SPDX-License-Identifier: Apache-2.0
package com.hedera.hashgraph.sdk.test.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeeEstimateQueryIntegrationTest {

    private static final long MIRROR_SYNC_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2);

    private static void waitForMirrorNodeSync() throws InterruptedException {
        Thread.sleep(MIRROR_SYNC_DELAY_MILLIS);
    }

    private static long subtotal(FeeEstimate estimate) {
        return estimate.getBase()
                + estimate.getExtras().stream().mapToLong(FeeExtra::getSubtotal).sum();
    }

    private static void assertFeeComponentsPresent(FeeEstimateResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.getNetwork()).isNotNull();
        assertThat(response.getNode()).isNotNull();
        assertThat(response.getService()).isNotNull();
        assertThat(response.getNotes()).isNotNull();
    }

    private static void assertComponentTotalsConsistent(FeeEstimateResponse response) {
        var network = response.getNetwork();
        var node = response.getNode();
        var service = response.getService();

        var nodeSubtotal = subtotal(node);
        var serviceSubtotal = subtotal(service);

        assertThat(network.getSubtotal()).isEqualTo(nodeSubtotal * network.getMultiplier());
        assertThat(response.getTotal()).isEqualTo(network.getSubtotal() + nodeSubtotal + serviceSubtotal);
    }

    @Test
    @DisplayName("Given a TokenCreateTransaction, when fee estimate is requested, "
            + "then response includes service fees for token creation and network fees")
    void tokenCreateTransactionFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {

            // Given: A TokenCreateTransaction is created
            var transaction = new TokenCreateTransaction()
                    .setTokenName("Test Token")
                    .setTokenSymbol("TEST")
                    .setDecimals(3)
                    .setInitialSupply(1000000)
                    .setTreasuryAccountId(testEnv.operatorId)
                    .setAdminKey(testEnv.operatorKey)
                    .freezeWith(testEnv.client)
                    .signWithOperator(testEnv.client);

            waitForMirrorNodeSync();

            // When: A fee estimate is requested
            FeeEstimateResponse response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.STATE)
                    .execute(testEnv.client);

            // Then: The response includes appropriate fees
            assertFeeComponentsPresent(response);
            assertThat(response.getMode()).isEqualTo(FeeEstimateMode.STATE);
            assertComponentTotalsConsistent(response);
            // Log the fee breakdown for debugging
            System.out.println("Token Create Fee Estimate:");
            System.out.println("  Mode: " + response.getMode());
            System.out.println("  Service Base: " + response.getService().getBase());
            System.out.println("  Network Subtotal: " + response.getNetwork().getSubtotal());
            System.out.println("  Node Base: " + response.getNode().getBase());
            System.out.println("  Total: " + response.getTotal());
        }
    }

    @Test
    @DisplayName(
            "Given a TransferTransaction, when fee estimate is requested in STATE mode, then all components are returned")
    void transferTransactionStateModeFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TransferTransaction()
                    .addHbarTransfer(testEnv.operatorId, Hbar.fromTinybars(-1))
                    .addHbarTransfer(AccountId.fromString("0.0.3"), Hbar.fromTinybars(1))
                    .freezeWith(testEnv.client)
                    .signWithOperator(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.STATE)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertThat(response.getMode()).isEqualTo(FeeEstimateMode.STATE);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @Disabled
    @DisplayName(
            "Given a TransferTransaction, when fee estimate is requested in INTRINSIC mode, then components are returned without state dependencies")
    void transferTransactionIntrinsicModeFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TransferTransaction()
                    .addHbarTransfer(testEnv.operatorId, Hbar.fromTinybars(-1))
                    .addHbarTransfer(AccountId.fromString("0.0.3"), Hbar.fromTinybars(1))
                    .freezeWith(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.INTRINSIC)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertThat(response.getMode()).isEqualTo(FeeEstimateMode.INTRINSIC);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @DisplayName(
            "Given a TransferTransaction without explicit mode, when fee estimate is requested, then STATE mode is used by default")
    void transferTransactionDefaultModeIsState() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TransferTransaction()
                    .addHbarTransfer(testEnv.operatorId, Hbar.fromTinybars(-1))
                    .addHbarTransfer(AccountId.fromString("0.0.3"), Hbar.fromTinybars(1))
                    .freezeWith(testEnv.client)
                    .signWithOperator(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery().setTransaction(transaction).execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertThat(response.getMode()).isEqualTo(FeeEstimateMode.STATE);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @DisplayName("Given a TokenMintTransaction, when fee estimate is requested, then extras are returned for minting")
    void tokenMintTransactionFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TokenMintTransaction()
                    .setTokenId(TokenId.fromString("0.0.1234"))
                    .setAmount(10)
                    .freezeWith(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.INTRINSIC)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertThat(response.getNode().getExtras()).isNotNull();
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @DisplayName("Given a TopicCreateTransaction, when fee estimate is requested, then service fees are included")
    void topicCreateTransactionFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TopicCreateTransaction()
                    .setTopicMemo("integration test topic")
                    .freezeWith(testEnv.client)
                    .signWithOperator(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.STATE)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @DisplayName("Given a ContractCreateTransaction, when fee estimate is requested, then execution fees are returned")
    void contractCreateTransactionFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new ContractCreateTransaction()
                    .setBytecode(new byte[] {1, 2, 3})
                    .setGas(1000)
                    .setAdminKey(testEnv.operatorKey)
                    .freezeWith(testEnv.client)
                    .signWithOperator(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.STATE)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @DisplayName("Given a FileCreateTransaction, when fee estimate is requested, then storage fees are included")
    void fileCreateTransactionFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new FileCreateTransaction()
                    .setKeys(testEnv.operatorKey)
                    .setContents("integration test file")
                    .freezeWith(testEnv.client)
                    .signWithOperator(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.STATE)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @Disabled
    @DisplayName(
            "Given a FileAppendTransaction spanning multiple chunks, when fee estimate is requested, then aggregated totals are returned")
    void fileAppendTransactionFeeEstimateAggregatesChunks() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new FileAppendTransaction()
                    .setFileId(FileId.fromString("0.0.1234"))
                    .setContents(new byte[5000])
                    .freezeWith(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.INTRINSIC)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @DisplayName(
            "Given a TopicMessageSubmitTransaction smaller than a chunk, when fee estimate is requested, then a single chunk estimate is returned")
    void topicMessageSubmitSingleChunkFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TopicMessageSubmitTransaction()
                    .setTopicId(TopicId.fromString("0.0.1234"))
                    .setMessage(new byte[128])
                    .freezeWith(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.INTRINSIC)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertComponentTotalsConsistent(response);
        }
    }

    @Test
    @Disabled
    @DisplayName(
            "Given a TopicMessageSubmitTransaction larger than a chunk, when fee estimate is requested, then multi-chunk totals are aggregated")
    void topicMessageSubmitMultipleChunkFeeEstimate() throws Throwable {
        try (var testEnv = new IntegrationTestEnv(1)) {
            var transaction = new TopicMessageSubmitTransaction()
                    .setTopicId(TopicId.fromString("0.0.1234"))
                    .setMessage(new byte[5000])
                    .freezeWith(testEnv.client);

            waitForMirrorNodeSync();

            var response = new FeeEstimateQuery()
                    .setTransaction(transaction)
                    .setMode(FeeEstimateMode.INTRINSIC)
                    .execute(testEnv.client);

            assertFeeComponentsPresent(response);
            assertComponentTotalsConsistent(response);
        }
    }
}
