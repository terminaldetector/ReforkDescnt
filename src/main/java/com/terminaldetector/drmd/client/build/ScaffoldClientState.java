package com.terminaldetector.drmd.client.build;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-side locked scaffold frame from server. */
public final class ScaffoldClientState {
	public static final ScaffoldClientState INSTANCE = new ScaffoldClientState();

	private volatile boolean active;
    private volatile boolean constructionMode,modeKnown;
	private volatile String shapeId = "";
	private volatile List<BlockPos> positions = List.of();

	private ScaffoldClientState() {}

	public void set(boolean active, String shapeId, List<BlockPos> positions) {
		this.active = active;
		this.shapeId = shapeId == null ? "" : shapeId;
		if (positions == null || positions.isEmpty()) {
			this.positions = List.of();
		} else {
			this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
		}
	}

	public void clear() {
		set(false, "", List.of());constructionMode=false;modeKnown=false;
	}

	public void setConstructionMode(boolean active){constructionMode=active;modeKnown=true;}
    public boolean constructionMode(){return constructionMode;}
    public boolean modeKnown(){return modeKnown;}
	public boolean active() { return active && !positions.isEmpty(); }
	public String shapeId() { return shapeId; }
	public List<BlockPos> positions() { return positions; }
}
