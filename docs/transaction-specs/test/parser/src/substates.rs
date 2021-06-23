extern crate bytebuffer;

use bytebuffer::ByteBuffer;
use core::fmt::Debug;

use crate::types::Address;
use crate::types::PublicKey;
use crate::types::U256;
use crate::types::UTF8;

pub trait Substate: std::fmt::Debug {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self
    where
        Self: Sized;
}

#[derive(Debug)]
pub struct REAddress {
    pub address: Address,
}

#[derive(Debug)]
pub struct TokenDefinition {
    pub rri: Address,
    pub token_type: u8,
    pub supply: Option<U256>,
    pub minter: Option<Address>,
    pub name: UTF8,
    pub description: UTF8,
    pub url: UTF8,
    pub icon_url: UTF8,
}

#[derive(Debug)]
pub struct Tokens {
    pub rri: Address,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct PreparedStake {
    pub owner: Address,
    pub delegate: PublicKey,
    pub amount: U256,
}

#[derive(Debug)]
pub struct StakeShare {
    pub delegate: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct PreparedUnstake {
    pub delegate: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct ExitingStake {
    pub epoch_unlocked: u64,
    pub delegate: PublicKey,
    pub amount: U256,
    pub ownership: U256,
}

#[derive(Debug)]
pub struct Validator {
    pub key: PublicKey,
    pub is_registered: u8,
    pub name: UTF8,
    pub url: UTF8,
}

#[derive(Debug)]
pub struct Unique {
    pub address: Address,
}

impl Substate for REAddress {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            address: Address::from_buffer(buffer),
        }
    }
}

impl Substate for TokenDefinition {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let rri = Address::from_buffer(buffer);
        let token_type = buffer.read_u8();
        let supply = match token_type {
            0x02 => Some(U256::from_buffer(buffer)),
            _ => None,
        };
        let minter = match token_type {
            0x01 => Some(Address::from_buffer(buffer)),
            _ => None,
        };
        let name = UTF8::from_buffer(buffer);
        let description = UTF8::from_buffer(buffer);
        let url = UTF8::from_buffer(buffer);
        let icon_url = UTF8::from_buffer(buffer);

        Self {
            rri,
            token_type,
            supply,
            minter,
            name,
            description,
            url,
            icon_url,
        }
    }
}

impl Substate for Tokens {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let rri = Address::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self { rri, owner, amount }
    }
}

impl Substate for PreparedStake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let owner = Address::from_buffer(buffer);
        let delegate = PublicKey::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            owner,
            delegate,
            amount,
        }
    }
}

impl Substate for StakeShare {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let delegate = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            delegate,
            owner,
            amount,
        }
    }
}

impl Substate for PreparedUnstake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let delegate = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            delegate,
            owner,
            amount,
        }
    }
}

impl Substate for ExitingStake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let epoch_unlocked = buffer.read_u64();
        let delegate = PublicKey::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);
        let ownership = U256::from_buffer(buffer);

        Self {
            epoch_unlocked,
            delegate,
            amount,
            ownership,
        }
    }
}

impl Substate for Validator {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let key = PublicKey::from_buffer(buffer);
        let is_registered = buffer.read_u8();
        let name = UTF8::from_buffer(buffer);
        let url = UTF8::from_buffer(buffer);

        Self {
            key,
            is_registered,
            name,
            url,
        }
    }
}

impl Substate for Unique {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let address = Address::from_buffer(buffer);

        Self { address }
    }
}
