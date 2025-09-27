package com.sharenote.redistribution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DataSourceType {
    LEGACY("legacy"),
    SHARD1("shard1"),
    SHARD2("shard2");

    private final String key;
}
