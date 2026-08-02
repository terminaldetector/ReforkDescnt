package com.terminaldetector.drmd.workshop;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * One construction module — port of SCK / D6_SCKBridge flat module format:
 * { cluster, slot, name, type, model, pos, angle, size, color, anim, attach }
 */
public class ClusterModule {
	public String cluster = "Center";
	public int slot;
	public String name = "module";
	public String type = "Model"; // Model | Sprite | Quad
	public String model = "barrel"; // logical MC part id
	public float posFwd, posRgt, posUp;
	public float pitch, yaw, roll;
	public float scaleX = 1f, scaleY = 1f, scaleZ = 1f;
	public int colorR = 255, colorG = 255, colorB = 255, colorA = 255;
	public boolean muzzle;
	public int muzzleIdx = 1;
	public float spin;
	public float bobAmp;
	public float bobFreq;
	public float kick;

	public ClusterModule copy() {
		ClusterModule m = new ClusterModule();
		m.cluster = cluster;
		m.slot = slot;
		m.name = name;
		m.type = type;
		m.model = model;
		m.posFwd = posFwd;
		m.posRgt = posRgt;
		m.posUp = posUp;
		m.pitch = pitch;
		m.yaw = yaw;
		m.roll = roll;
		m.scaleX = scaleX;
		m.scaleY = scaleY;
		m.scaleZ = scaleZ;
		m.colorR = colorR;
		m.colorG = colorG;
		m.colorB = colorB;
		m.colorA = colorA;
		m.muzzle = muzzle;
		m.muzzleIdx = muzzleIdx;
		m.spin = spin;
		m.bobAmp = bobAmp;
		m.bobFreq = bobFreq;
		m.kick = kick;
		return m;
	}

	public NbtCompound toNbt() {
		NbtCompound n = new NbtCompound();
		n.putString("cluster", cluster);
		n.putInt("slot", slot);
		n.putString("name", name);
		n.putString("type", type);
		n.putString("model", model);
		n.putFloat("fwd", posFwd);
		n.putFloat("rgt", posRgt);
		n.putFloat("up", posUp);
		n.putFloat("pitch", pitch);
		n.putFloat("yaw", yaw);
		n.putFloat("roll", roll);
		n.putFloat("sx", scaleX);
		n.putFloat("sy", scaleY);
		n.putFloat("sz", scaleZ);
		n.putInt("cr", colorR);
		n.putInt("cg", colorG);
		n.putInt("cb", colorB);
		n.putInt("ca", colorA);
		n.putBoolean("muzzle", muzzle);
		n.putInt("muzzleIdx", muzzleIdx);
		n.putFloat("spin", spin);
		n.putFloat("bobAmp", bobAmp);
		n.putFloat("bobFreq", bobFreq);
		n.putFloat("kick", kick);
		return n;
	}

	public static ClusterModule fromNbt(NbtCompound n) {
		ClusterModule m = new ClusterModule();
		m.cluster = n.contains("cluster") ? n.getString("cluster") : "Center";
		m.slot = n.getInt("slot");
		m.name = n.contains("name") ? n.getString("name") : "module";
		m.type = n.contains("type") ? n.getString("type") : "Model";
		m.model = n.contains("model") ? n.getString("model") : "barrel";
		m.posFwd = n.getFloat("fwd");
		m.posRgt = n.getFloat("rgt");
		m.posUp = n.getFloat("up");
		m.pitch = n.getFloat("pitch");
		m.yaw = n.getFloat("yaw");
		m.roll = n.getFloat("roll");
		m.scaleX = n.contains("sx") ? n.getFloat("sx") : 1f;
		m.scaleY = n.contains("sy") ? n.getFloat("sy") : 1f;
		m.scaleZ = n.contains("sz") ? n.getFloat("sz") : 1f;
		m.colorR = n.contains("cr") ? n.getInt("cr") : 255;
		m.colorG = n.contains("cg") ? n.getInt("cg") : 255;
		m.colorB = n.contains("cb") ? n.getInt("cb") : 255;
		m.colorA = n.contains("ca") ? n.getInt("ca") : 255;
		m.muzzle = n.getBoolean("muzzle");
		m.muzzleIdx = n.contains("muzzleIdx") ? n.getInt("muzzleIdx") : 1;
		m.spin = n.getFloat("spin");
		m.bobAmp = n.getFloat("bobAmp");
		m.bobFreq = n.getFloat("bobFreq");
		m.kick = n.getFloat("kick");
		return m;
	}

	public static NbtList listToNbt(java.util.List<ClusterModule> mods) {
		NbtList list = new NbtList();
		for (ClusterModule m : mods) list.add(m.toNbt());
		return list;
	}

	public static java.util.List<ClusterModule> listFromNbt(NbtList list) {
		java.util.ArrayList<ClusterModule> out = new java.util.ArrayList<>();
		for (int i = 0; i < list.size(); i++) out.add(fromNbt(list.getCompound(i)));
		return out;
	}
}
