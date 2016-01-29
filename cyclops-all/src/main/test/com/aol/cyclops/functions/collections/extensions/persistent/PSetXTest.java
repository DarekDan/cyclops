package com.aol.cyclops.functions.collections.extensions.persistent;

import com.aol.cyclops.collections.extensions.CollectionX;
import com.aol.cyclops.collections.extensions.persistent.PSetX;
import com.aol.cyclops.functions.collections.extensions.CollectionXTestsWithNulls;

public class PSetXTest extends CollectionXTestsWithNulls{

	@Override
	public <T> CollectionX<T> of(T... values) {
		return PSetX.of(values);
	}

}
