// Copyright (c) 2015 The original author or authors
//
// This software may be modified and distributed under the terms
// of the zlib license.  See the LICENSE file for details.

package com.badlogic.gdx.spriter;

import com.badlogic.gdx.spriter.data.SpriterAnimation;
import com.badlogic.gdx.spriter.data.SpriterData;
import com.badlogic.gdx.spriter.data.SpriterKey;
import com.badlogic.gdx.spriter.data.SpriterMainlineKey;
import com.badlogic.gdx.spriter.data.SpriterObject;
import com.badlogic.gdx.spriter.data.SpriterObjectRef;
import com.badlogic.gdx.spriter.data.SpriterRef;
import com.badlogic.gdx.spriter.data.SpriterSpatial;
import com.badlogic.gdx.spriter.data.SpriterTimeline;
import com.badlogic.gdx.spriter.data.SpriterTimelineKey;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

public class FrameData {

	public final Array<SpriterObject> spriteData = new Array<SpriterObject>();
	public final Array<SpriterObject> pointData = new Array<SpriterObject>();
	public final IntMap<SpriterObject> boxData = new IntMap<SpriterObject>();
	
	@Override
	public String toString() {
		return "FrameData [spriteData=" + spriteData + ", pointData=" + pointData + ", boxData=" + boxData + "]";
	}
	
	public static FrameData create(SpriterAnimation first, SpriterAnimation second, float targetTime, float factor) {

		if (first == second)
			return create(first, targetTime);

		float targetTimeSecond = targetTime / first.length * second.length;

		SpriterMainlineKey[] keys = getMainlineKeys(first.mainline.keys.toArray(SpriterMainlineKey.class), targetTime);
		SpriterMainlineKey firstKeyA = keys[0];
		SpriterMainlineKey firstKeyB = keys[1];

		keys = getMainlineKeys(second.mainline.keys.toArray(SpriterMainlineKey.class), targetTimeSecond);
		SpriterMainlineKey secondKeyA = keys[0];
		SpriterMainlineKey secondKeyB = keys[1];

		if (firstKeyA.boneRefs.size != secondKeyA.boneRefs.size || firstKeyB.boneRefs.size != secondKeyB.boneRefs.size || firstKeyA.objectRefs.size != secondKeyA.objectRefs.size || firstKeyB.objectRefs.size != secondKeyB.objectRefs.size)
			return create(first, targetTime);

		float adjustedTimeFirst = adjustTime(firstKeyA, firstKeyB, first.length, targetTime);
		float adjustedTimeSecond = adjustTime(secondKeyA, secondKeyB, second.length, targetTimeSecond);

		SpriterSpatial[] boneInfosA = getBoneInfos(firstKeyA, first, adjustedTimeFirst);
		SpriterSpatial[] boneInfosB = getBoneInfos(secondKeyA, second, adjustedTimeSecond);
		SpriterSpatial[] boneInfos = null;

		if (boneInfosA != null && boneInfosB != null) {
			boneInfos = new SpriterSpatial[boneInfosA.length];
			for (int i = 0; i < boneInfosA.length; ++i) {
				SpriterSpatial boneA = boneInfosA[i];
				SpriterSpatial boneB = boneInfosB[i];
				SpriterSpatial interpolated = interpolate(boneA, boneB, factor, 1);
				interpolated.angle = MathHelper.closerAngleLinear(boneA.angle, boneB.angle, factor);
				boneInfos[i] = interpolated;
			}
		}

		SpriterMainlineKey baseKey = factor < 0.5f ? firstKeyA : firstKeyB;
		SpriterAnimation currentAnimation = factor < 0.5f ? first : second;

		FrameData frameData = new FrameData();

		for (int i = 0; i < baseKey.objectRefs.size; ++i) {
			SpriterObjectRef objectRefFirst = baseKey.objectRefs.get(i);
			SpriterObject interpolatedFirst = getObjectInfo(objectRefFirst, first, adjustedTimeFirst);

			SpriterObjectRef objectRefSecond = secondKeyA.objectRefs.get(i);
			SpriterObject interpolatedSecond = getObjectInfo(objectRefSecond, second, adjustedTimeSecond);

			SpriterObject info = interpolate(interpolatedFirst, interpolatedSecond, factor, 1);
			info.angle = MathHelper.closerAngleLinear(interpolatedFirst.angle, interpolatedSecond.angle, factor);

			if (boneInfos != null && objectRefFirst.parentId >= 0)
				applyParentTransform(info, boneInfos[objectRefFirst.parentId]);

			addSpatialData(info, currentAnimation.timelines.get(objectRefFirst.timelineId), currentAnimation.entity.data, targetTime, frameData);
		}

		return frameData;
	}
	
	public static FrameData create(SpriterAnimation animation, float targetTime) {
		return create(animation, targetTime, null);
	}
	
	public static FrameData create(SpriterAnimation animation, float targetTime, SpriterSpatial parentInfo) {
		SpriterMainlineKey[] keys = animation.mainline.keys.toArray(SpriterMainlineKey.class);
		keys = getMainlineKeys(keys, targetTime);
		SpriterMainlineKey keyA = keys[0];
		SpriterMainlineKey keyB = keys[1];

		float adjustedTime = adjustTime(keyA, keyB, animation.length, targetTime);

		SpriterSpatial[] boneInfos = getBoneInfos(keyA, animation, targetTime, parentInfo);

		FrameData frameData = new FrameData();

		for (SpriterObjectRef objectRef : keyA.objectRefs) {
			SpriterObject interpolated = getObjectInfo(objectRef, animation, adjustedTime);

			if (boneInfos != null && objectRef.parentId >= 0)
				applyParentTransform(interpolated, boneInfos[objectRef.parentId]);

			addSpatialData(interpolated, animation.timelines.get(objectRef.timelineId), animation.entity.data, targetTime, frameData);
		}

		return frameData;
	}
	
	private static void addSpatialData(SpriterObject info, SpriterTimeline timeline, SpriterData spriter, float targetTime, FrameData frameData) {
		switch (timeline.objectType) {
		case Sprite:
			frameData.spriteData.add(info);
			break;
		case Entity:
			SpriterAnimation newAnim = spriter.entities.get(info.entityId).animations.get(info.animationId);
			float newTargetTime = info.t * newAnim.length;
			frameData.spriteData.addAll(FrameData.create(newAnim, newTargetTime, info).spriteData);
			break;
		case Point:
			frameData.pointData.add(info);
			break;
		case Box:
			frameData.boxData.put(timeline.objectId, info);
			break;
		default:
			break;
		}
	}

	private static SpriterSpatial[] getBoneInfos(SpriterMainlineKey key, SpriterAnimation animation, float targetTime) {
		return getBoneInfos(key, animation, targetTime, null);
	}
	
	private static SpriterSpatial[] getBoneInfos(SpriterMainlineKey key, SpriterAnimation animation, float targetTime, SpriterSpatial parentInfo) {
		if (key.boneRefs == null)
			return null;
		SpriterSpatial[] ret = new SpriterSpatial[key.boneRefs.size];

		for (int i = 0; i < key.boneRefs.size; ++i) {
			SpriterRef boneRef = key.boneRefs.get(i);
			SpriterSpatial interpolated = getBoneInfo(boneRef, animation, targetTime);

			if (boneRef.parentId >= 0)
				applyParentTransform(interpolated, ret[boneRef.parentId]);
			else if (parentInfo != null)
				applyParentTransform(interpolated, parentInfo);
			ret[i] = interpolated;
		}

		return ret;
	}

	
	
	private static SpriterMainlineKey[] getMainlineKeys(SpriterMainlineKey[] keys, float targetTime) {
		SpriterMainlineKey keyA = lastKeyForTime(keys, targetTime);
		int nextKey = keyA.id + 1;
		if (nextKey >= keys.length)
			nextKey = 0;
		SpriterMainlineKey keyB = keys[nextKey];

		return new SpriterMainlineKey[] { keyA, keyB };
	}

	private static SpriterSpatial getBoneInfo(SpriterRef spriterRef, SpriterAnimation animation, float targetTime) {
		SpriterTimeline timeline = animation.timelines.get(spriterRef.timelineId);
		SpriterTimelineKey[] keys = timeline.keys.toArray(SpriterTimelineKey.class);
		SpriterTimelineKey keyA = keys[spriterRef.keyId];
		SpriterTimelineKey keyB = getNextXLineKey(keys, keyA, animation.looping);

		if (keyB == null)
			return copy(keyA.boneInfo);

		float factor = getFactor(keyA, keyB, animation.length, targetTime);
		return interpolate(keyA.boneInfo, keyB.boneInfo, factor, keyA.spin);
	}

	private static SpriterObject getObjectInfo(SpriterRef spriterRef, SpriterAnimation animation, float targetTime) {
		SpriterTimelineKey[] keys = animation.timelines.get(spriterRef.timelineId).keys.toArray(SpriterTimelineKey.class);
		SpriterTimelineKey keyA = keys[spriterRef.keyId];
		SpriterTimelineKey keyB = getNextXLineKey(keys, keyA, animation.looping);

		if (keyB == null)
			return copy(keyA.objectInfo);

		float factor = getFactor(keyA, keyB, animation.length, targetTime);
		return interpolate(keyA.objectInfo, keyB.objectInfo, factor, keyA.spin);
	}

	

	

	private static SpriterSpatial interpolate(SpriterSpatial a, SpriterSpatial b, float f, int spin) {
		SpriterSpatial spatial = new SpriterSpatial();

		spatial.angle = MathHelper.angleLinear(a.angle, b.angle, spin, f);
		spatial.x = MathHelper.linear(a.x, b.x, f);
		spatial.y = MathHelper.linear(a.y, b.y, f);
		spatial.scaleX = MathHelper.linear(a.scaleX, b.scaleX, f);
		spatial.scaleY = MathHelper.linear(a.scaleY, b.scaleY, f);

		return spatial;
	}

	private static SpriterObject interpolate(SpriterObject a, SpriterObject b, float f, int spin) {
		SpriterObject object = new SpriterObject();

		object.angle = MathHelper.angleLinear(a.angle, b.angle, spin, f);
		object.alpha = MathHelper.linear(a.alpha, b.alpha, f);
		object.x = MathHelper.linear(a.x, b.x, f);
		object.y = MathHelper.linear(a.y, b.y, f);
		object.scaleX = MathHelper.linear(a.scaleX, b.scaleX, f);
		object.scaleY = MathHelper.linear(a.scaleY, b.scaleY, f);
		object.pivotX = a.pivotX;
		object.pivotY = a.pivotY;
		object.fileId = a.fileId;
		object.folderId = a.folderId;
		object.entityId = a.entityId;
		object.animationId = a.animationId;
		object.t = MathHelper.linear(a.t, b.t, f);

		return object;
	}

	private static void applyParentTransform(SpriterSpatial child, SpriterSpatial parent) {
		float px = parent.scaleX * child.x;
		float py = parent.scaleY * child.y;
		double angleRad = parent.angle * Math.PI / 180;
		float s = (float) Math.sin(angleRad);
		float c = (float) Math.cos(angleRad);

		child.x = px * c - py * s + parent.x;
		child.y = px * s + py * c + parent.y;
		child.scaleX *= parent.scaleX;
		child.scaleY *= parent.scaleY;
		child.angle = parent.angle + Math.signum(parent.scaleX * parent.scaleY) * child.angle;
		child.angle %= 360.0f;
	}

	private static SpriterSpatial copy(SpriterSpatial info) {
		SpriterSpatial copy = new SpriterSpatial();
		fillFrom(copy, info);
		return copy;
	}

	private static SpriterObject copy(SpriterObject info) {
		SpriterObject copy = new SpriterObject();

		copy.animationId = info.animationId;
		copy.entityId = info.entityId;
		copy.fileId = info.fileId;
		copy.folderId = info.folderId;
		copy.pivotX = info.pivotX;
		copy.pivotY = info.pivotY;
		copy.t = info.t;

		fillFrom(copy, info);
		return copy;
	}

	private static void fillFrom(SpriterSpatial target, SpriterSpatial source) {
		target.alpha = source.alpha;
		target.angle = source.angle;
		target.scaleX = source.scaleX;
		target.scaleY = source.scaleY;
		target.x = source.x;
		target.y = source.y;
	}

	public static float adjustTime(SpriterKey keyA, SpriterKey keyB, float animationLength, float targetTime) {
		float nextTime = keyB.time > keyA.time ? keyB.time : animationLength;
		float factor = getFactor(keyA, keyB, animationLength, targetTime);
		return MathHelper.linear(keyA.time, nextTime, factor);
	}
	
	public static float getFactor(SpriterKey keyA, SpriterKey keyB, float animationLength, float targetTime) {
		float timeA = keyA.time;
		float timeB = keyB.time;

		if (timeA > timeB) {
			timeB += animationLength;
			if (targetTime < timeA)
				targetTime += animationLength;
		}

		float factor = MathHelper.reverseLinear(timeA, timeB, targetTime);
		factor = keyA.curveType.applySpeedCurve(keyA, factor);
		return factor;
	}
	
	public static <T extends SpriterKey> T lastKeyForTime(T[] keys, float targetTime) {
		T current = null;
		for (T key : keys) {
			if (key.time > targetTime)
				break;
			current = key;
		}

		return current;
	}

	public static <T extends SpriterKey> T getNextXLineKey(T[] keys, T firstKey, boolean looping) {
		if (keys.length == 1)
			return null;

		int keyBId = firstKey.id + 1;
		if (keyBId >= keys.length) {
			if (!looping)
				return null;
			keyBId = 0;
		}

		return keys[keyBId];
	}
	
}
