package com.psdk.clan;

import java.util.UUID;

public record ClanMember(UUID uuid, String name, String role, long joinedAt) {}
