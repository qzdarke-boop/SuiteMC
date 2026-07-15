package com.psdk.thepit.topboard;

import java.util.UUID;

public record TopEntry(UUID uuid, String name, double value, String clanTag, String clanColor) {}
