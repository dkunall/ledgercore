package com.ledgercore.transfer.application;

public record TransferResult(TransferView transfer, boolean replayed) {
}
