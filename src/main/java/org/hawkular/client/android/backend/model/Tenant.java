package org.hawkular.client.android.backend.model;

import com.google.gson.annotations.SerializedName;

public final class Tenant
{
	@SerializedName("id")
	private String id;

	public String getId() {
		return id;
	}
}
