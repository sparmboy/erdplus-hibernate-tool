package com.sarm.tools.erdplus.model;

import java.util.List;

/**
 * Created by spencer on 16/07/2016.
 */
public class ERDPlusDetails {
    public String name;
    public String type;
    public int id;
    public int slotIndex;
    public boolean isIdentifying;
    public boolean isDerived;
    public boolean isMultivalued;
    public boolean isOptional;
    public boolean isCompostie;
    public boolean isUnique;
    public List<ERDPlusSlot> slots;
}
