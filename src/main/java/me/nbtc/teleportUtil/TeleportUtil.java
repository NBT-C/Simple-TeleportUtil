import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/*
 * TeleportUtil.java
 * Author: NBTC
 * Copyright (c) 2024 NBTC. All Rights Reserved.
 *
 * This utility class provides optimized methods for teleporting players
 * to specific locations. It ensures minimal performance impact by offloading
 * teleportation tasks, pre-fetching chunks, and splitting teleportation into batches.
 *
 * Licensed under the MIT License. See LICENSE file for details.
 */
public class TeleportUtil {

    // ExecutorService for offloading teleportation tasks
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    // Optimized teleport function that handles both List<Player> and Collection<Player> with List<Location>
    public static void teleport(List<Player> players, List<Location> locations) {
        // Pre-fetch chunks for all teleportation locations to prevent lag during teleportation
        locations.forEach(TeleportUtil::loadChunkAsync);

        // Split players into batches
        List<List<Player>> batches = partition(players);

        // Process each batch with a delay to avoid overwhelming the system
        for (List<Player> batch : batches) {
            // Offload the batch processing to the executor service
            executorService.submit(() -> processBatch(batch, locations));
        }
    }

    private static void processBatch(List<Player> players, List<Location> locations) {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location location = locations.get(i % locations.size());  // Wrap around locations if players exceed locations

            // Ensure chunk is loaded before teleportation
            loadChunkAsync(location);

            // Teleport player after chunk is loaded
            Bukkit.getScheduler().runTask(your_instance, () -> teleportPlayer(player, location));
        }
    }

    private static <T> List<List<T>> partition(List<T> list) {
        return new ArrayList<>(list.stream()
                .collect(Collectors.groupingBy(s -> list.indexOf(s) / 20)) // <- Batch Size (20)
                .values());
    }

    // Optimized teleport function for a Collection of players and List of Locations
    public static void teleport(Collection<Player> players, List<Location> locations) {
        // Convert Collection to List and call the List method
        teleport(new ArrayList<>(players), locations);
    }

    // Batch teleport function for a List of players to a single location (optimized for large groups)
    public static void teleport(List<Player> players, Location location) {
        int batchSize = 20; // You can adjust the batch size to fit your needs (20 is usually a good balance)

        // Pre-fetch chunk of the target location before teleporting players
        loadChunkAsync(location);

        // Split into batches for better performance
        for (int i = 0; i < players.size(); i += batchSize) {
            int end = Math.min(i + batchSize, players.size());
            List<Player> batch = players.subList(i, end);

            // Execute the teleport in one go for each batch, but offloaded to a separate thread to prevent blocking
            Bukkit.getScheduler().runTask(your_instance, () -> {
                batch.forEach(player -> teleportPlayer(player, location));
            });
        }
    }

    // Optimized teleport function for a Collection of players to a single location
    public static void teleport(Collection<Player> players, Location location) {
        teleport(new ArrayList<>(players), location); // Convert Collection to List and call the List version
    }

    // Teleports a single player to the provided location
    private static void teleportPlayer(Player player, Location location) {
        if (player == null || location == null) return;  // Early exit if the player or location is invalid

        // Ensure the player is online before attempting to teleport
        if (player.isOnline()) {
            player.teleport(location);
        }
    }

    // Pre-load the chunk asynchronously to ensure teleportation happens smoothly without chunk loading delays
    private static void loadChunkAsync(Location location) {
        // Load the chunk asynchronously so it won't block the main thread
        Bukkit.getScheduler().runTask(your_instance, () -> {
            Chunk chunk = location.getWorld().getChunkAt(location);
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
        });
    }

    // Clean up resources when the plugin is disabled
    public static void shutdownExecutorService() {
        if (!executorService.isShutdown()) {
            executorService.shutdown();  // Shutdown executor when no longer needed
        }
    }
}
