// Copyright (c) 2015 The original author or authors
//
// This software may be modified and distributed under the terms
// of the zlib license.  See the LICENSE file for details.

package com.badlogic.gdx.spriter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Assert;
import org.junit.Test;

import com.badlogic.gdx.spriter.data.SpriterData;
import com.badlogic.gdx.spriter.io.SconReader;

public class SconTest {

	@Test
	public void readScon() throws IOException {
		for (String scon : SpriterTestData.scon) {

			Assert.assertNotNull("SCON file missing", getClass().getResource(scon));

			Reader r = new InputStreamReader(getClass().getResourceAsStream(scon));

			SconReader reader = new SconReader();

			SpriterData stuff = reader.load(r);

			Assert.assertNotNull(stuff);
		}
	}

	@Test
	public void checkSconReadContent() throws IOException {
		String scon = SpriterTestData.letterbotSCON;
		SpriterData data = SpriterTestData.letterbotSCONData;

		Reader r = new InputStreamReader(getClass().getResourceAsStream(scon));
		SconReader reader = new SconReader();
		SpriterData sconData = reader.load(r);

		String ref = data.toString();
		String actual = sconData.toString();
		Assert.assertEquals(ref, actual);
	}
}