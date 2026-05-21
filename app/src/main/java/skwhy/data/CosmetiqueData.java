package skwhy.data;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.ArrayList;
import org.joml.Quaternionf;

import skwhy.data.Tail.TailNode;

public class CosmetiqueData {
    private List<CosmetiqueHat> hats;
    private List<Player> viewers;
    private DisplayGroupData back;
    private Tail tail;
    private Entity entity;
    private boolean selfHats;
    private boolean selfBack;
    private boolean selfTail;

    public CosmetiqueData(Entity entity, boolean selfHats, boolean selfBack, boolean selfTail) {
        this.entity = entity;
        this.hats = new ArrayList<CosmetiqueHat>();
        this.selfHats = selfHats;
        this.selfBack = selfBack;
        this.selfTail = selfTail;
    }

    public void addViewer(Player player) {
        if (!viewers.contains(player)) {
            viewers.add(player);
            for (CosmetiqueHat hat : hats) {
                hat.data.addViewer(player);
            }
            if (back != null) back.addViewer(player);
            if (tail != null) tail.addViewer(player);
        }
    }
    public void removeViewer(Player player) {
        if (viewers.contains(player)) {
            viewers.remove(player);
            for (CosmetiqueHat hat : hats) {
                hat.data.removeViewer(player);
            }
            if (back != null) back.removeViewer(player);
            if (tail != null) tail.removeViewer(player);
        }
    }

    public List<Player> getViewers() {
        return new ArrayList<>(viewers);
    }

    public void clearViewers() {
        viewers.clear();
        for (CosmetiqueHat hat : hats) {
            hat.data.clearViewers();
        }
        if (back != null) back.clearViewers();
        if (tail != null) tail.clearViewers();
    }
    public void setViewers(List<Player> viewers) {
        this.viewers = new ArrayList<>(viewers);
        for (CosmetiqueHat hat : hats) {
            hat.data.setViewers(viewers);
        }
        if (back != null) back.setViewers(viewers);
        if (tail != null) tail.setViewers(viewers);
    }

    public void update() {
        for (CosmetiqueHat hat : hats) {
            hat.update();
        }
        if (back != null) back.updateMetadata();
        if (tail != null) tail.nextFrame(entity.getLocation());
    }

    public void delete() {
        for (CosmetiqueHat hat : hats) {
            hat.delete();
        }
        if (back != null) back.delete();
        if (tail != null) tail.delete();
        if (entity != null) entity.remove();
        hats.clear();
        back = null;
        tail = null;
        viewers.clear();
    }

    // ─────── CHAPEAUX ───────

    public List<String> getHats() {
        return hats.stream().map(CosmetiqueHat::getSlot).toList();
    }
    public boolean getSelfHats() {
        return selfHats;
    }
    public void setSelfHats(boolean selfHats) {
        this.selfHats = selfHats;
    }
    public void setHat(DisplayGroupData data, String slot, boolean verticalRotation, boolean horizontalRotation) {
        removeHat(slot);
        this.hats.add(new CosmetiqueHat(data, slot, verticalRotation, horizontalRotation));
    }
    public void removeHat(String slot) {
        for (CosmetiqueHat hat : hats) {
            if (hat.getSlot().equals(slot)) {
                hat.delete();
                hats.remove(hat);
                break;
            }
        }
    }
    public void removeHats() {
        for (CosmetiqueHat hat : hats) {
            hat.delete();
            hats.remove(hat);
            break;
        }
    }

    private class CosmetiqueHat {
        private DisplayGroupData data;
        private String slot;
        private boolean verticalRotation;
        private boolean horizontalRotation;

        private CosmetiqueHat(DisplayGroupData data, String slot, boolean verticalRotation, boolean horizontalRotation) {
            data.setCenter(new Vec3(0f, -0.12f, 0f));
            data.setAttachedEntity(entity);
            List<Player> finalViewers = new ArrayList<>(viewers);
            if (entity instanceof Player p && selfHats) finalViewers.add(p);
            data.setViewers(finalViewers);;
            this.data = data;
            this.slot = slot;
            this.verticalRotation = verticalRotation;
            this.horizontalRotation = horizontalRotation;
        }
        private String getSlot() {
            return slot;
        }
        private void update() {
            if (verticalRotation) {
                data.setRotation(new Quat4(new Quaternionf().rotationXYZ((float) Math.toRadians(entity.getLocation().getYaw()), 0f, 0f)));
                data.updateMetadata();
            }
            if (horizontalRotation) {
                data.setYawPitch(entity.getLocation().getYaw(), 0);
                data.sendRotation();
            }
        }
        private void delete() {
            data.delete();
        }
    }


    // ─────── DOS ───────


    public DisplayGroupData getBack() {
        return back;
    }
    public boolean getSelfBack() {
        return selfBack;
    }
    public void setSelfBack(boolean selfBack) {
        this.selfBack = selfBack;
    }
    public void removeBack() {
        if (back != null) {
            back.delete();
            back = null;
        }
    }

    public void setBack(DisplayGroupData back) {
        back.setCenter(new Vec3(0f, -0.12f, 0.25f));
        back.setAttachedEntity(entity);
        List<Player> finalViewers = new ArrayList<>(viewers);
        if (entity instanceof Player p && selfBack) finalViewers.add(p);
        back.setViewers(finalViewers);
        this.back = back;
    }


    // ─────── QUEUES ───────


    public TailNode getTail() {
        return tail.getRoot();
    }
    public boolean getSelfTail() {
        return selfTail;
    }
    public void setSelfTail(boolean selfTail) {
        this.selfTail = selfTail;
    }
    public void setTail(TailNode tail) {
        this.tail = tail.getTailFromNode();
        this.tail.setViewers(viewers);
    }
    public void removeTail() {
        if (tail != null) {
            tail.delete();
            tail = null;
        }
    }

    // utilitaires
    public String serialize() {
        return "cosmetique:" + this.hashCode();
    }
}
