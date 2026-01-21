package hanamuramiyu.pawkin.net.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SchedulerManager {
    private final Plugin plugin;
    private final boolean isFolia;

    public SchedulerManager(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = checkFoliaViaClass();
    }

    private boolean checkFoliaViaClass() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean isFolia() {
        return isFolia;
    }

    public void runTask(Runnable task) {
        if (isFolia) {
            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
            scheduler.run(plugin, t -> task.run());
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTask(plugin, task);
        }
    }

    public void runTaskAsync(Runnable task) {
        if (isFolia) {
            AsyncScheduler scheduler = Bukkit.getAsyncScheduler();
            scheduler.runNow(plugin, t -> task.run());
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTaskAsynchronously(plugin, task);
        }
    }

    public void runTaskOnEntity(Entity entity, Runnable task) {
        if (isFolia) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTask(plugin, task);
        }
    }

    public void runTaskAtLocation(Location location, Runnable task) {
        if (isFolia) {
            RegionScheduler scheduler = Bukkit.getRegionScheduler();
            scheduler.run(plugin, location, t -> task.run());
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTask(plugin, task);
        }
    }

    public void runTaskLater(Runnable task, long delayTicks) {
        if (isFolia) {
            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
            scheduler.runDelayed(plugin, t -> task.run(), delayTicks);
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTaskLater(plugin, task, delayTicks);
        }
    }

    public void runTaskLaterAsync(Runnable task, long delayTicks) {
        if (isFolia) {
            AsyncScheduler scheduler = Bukkit.getAsyncScheduler();
            scheduler.runDelayed(plugin, t -> task.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    public void runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
            scheduler.runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public void runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            AsyncScheduler scheduler = Bukkit.getAsyncScheduler();
            scheduler.runAtFixedRate(plugin, t -> task.run(), 
                delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    public void cancelAllTasks() {
        if (isFolia) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            scheduler.cancelTasks(plugin);
        }
    }
}