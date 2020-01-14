package com.azquo.memorydb.core;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.namedata.DefaultDisplayNameOnly;
import com.azquo.memorydb.core.namedata.NameData;
import com.azquo.memorydb.service.NameService;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * This class represents a Name, a fundamental Azquo object. Names now have attributes and what was the name (as in the text) is now simply an attribute of the name
 * defined currently in a static below. Names can have parent and child relationships with multiple other names. Sets of names.
 * <p>
 * OK we want this object to only be modified if explicit functions are called, hence getters must not return mutable objects
 * and setters must make it clear what is going on. The class should be thread safe allowing concurrent reads if not concurrent writes.
 * Changes to state need not be immediately visible though in some cases code needs to be structured for suitable atomicity (e.g. attributes).
 * <p>
 * Thread safety is important, essentially modifying data will be synchronized, reads won't be and won't be completely up to date but should return consistent data.
 * <p>
 * This object was weighing in at over 2k average in an example magento db the goal is to bring it to sub 500 bytes.
 * This means in some cases variables being null until used and using arrays instead of sets switching for performance
 * when the list gets too big. I got it to about 850 bytes (magento example DB). Will park for the mo,
 * further change would probably involve changes to attributes, I mean the name of attributes not being held here for example.
 * <p>
 * Attributes case insensitive according to WFC spec.
 * <p>
 * Was comparable but this resulted in a code warning I've moved the comparator to NameService
 * <p>
 * Note : as well as heap overhead there's garbage collection overhead, watch for avoidable throw away objects.
 * <p>
 * Are we acquiring multiple locks in any places? Is it worth testing the cost of synchronizing access to bits of name? Depending on cost it could mean the code is simplified . . .
 * <p>
 * Also I'm using Double Checked Locking. With volatile DCL is correct according to the JMM,
 * Given that synchronization is not inherently expensive it might be worth considering how much places where I'm using DCL might actually be contended much.
 * <p>
 * I've extracted NameAttributes and a few static functions but there's still a fair amount of code in here. Values and children switching between arrays and sets might be a concern.
 *
 * I'm going to attempt to hive off everything except parents and provenance to a name data class. Prototyping in NewName first then move into name and comment properly when it's tested todo
 *
 */
public final class NewName {}